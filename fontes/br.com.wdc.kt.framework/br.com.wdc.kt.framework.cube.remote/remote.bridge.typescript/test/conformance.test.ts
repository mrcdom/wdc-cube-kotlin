import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, it } from 'vitest'
import { ScopeCoordinator } from '../src/ScopeCoordinator'
import { BROWSER_VSID } from '../src/constants'
import { FixtureMatcher } from './FixtureMatcher'

/**
 * Runs every client fixture of `conformance/client/` against this client, with a fake WebSocket: the runner plays the
 * host, the coordinator is the client under test.
 */

const FIXTURES = join(__dirname, '..', '..', 'conformance', 'client')

/** The sockets the client opened, newest last; what it sent on each. */
class FakeWebSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3
  static readonly opened: FakeWebSocket[] = []

  readyState = FakeWebSocket.CONNECTING
  readonly sent: string[] = []
  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null

  constructor(readonly url: string) {
    FakeWebSocket.opened.push(this)
    setTimeout(() => {
      this.readyState = FakeWebSocket.OPEN
      this.onopen?.(new Event('open'))
    }, 0)
  }

  send(text: string) {
    this.sent.push(text)
  }

  close() {
    if (this.readyState === FakeWebSocket.CLOSED) return
    this.readyState = FakeWebSocket.CLOSED
    setTimeout(() => this.onclose?.(new CloseEvent('close', { code: 1000 })), 0)
  }
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/** The message without the browser's keep-alive events; `null` if nothing but its request id is left. */
function withoutKeepAlives(message: Record<string, unknown>): Record<string, unknown> | null {
  const events = message.event as string[] | undefined
  if (events) {
    const kept = events.filter((it) => it !== `${BROWSER_VSID}:2`)
    if (kept.length === 0) delete message.event
    else message.event = kept
  }
  return Object.keys(message).every((key) => key === 'requestId') ? null : message
}

/** `"$serverKey"`: a new P-256 public key, as a host makes one to answer the handshake. */
async function withServerKey(message: Record<string, unknown>) {
  if (message.serverKey === '$serverKey') {
    const pair = await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'])
    const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey))
    message.serverKey = btoa(String.fromCharCode(...spki))
  }
  return message
}

async function runFixture(fixture: { steps: Record<string, any>[] }) {
  const matcher = new FixtureMatcher()
  let client: ScopeCoordinator | null = null
  let socket: FakeWebSocket | null = null
  let seen = 0

  const nextSent = async () => {
    for (let attempt = 0; attempt < 250; attempt++) {
      while (socket && seen < socket.sent.length) {
        const message = withoutKeepAlives(JSON.parse(socket.sent[seen++]))
        if (message) return message
      }
      await delay(20)
    }
    throw new Error('nothing was sent')
  }

  for (const [index, step] of fixture.steps.entries()) {
    try {
      if (step.start) {
        window.location.hash = step.start.hash
        sessionStorage.removeItem('app_id')
        const before = FakeWebSocket.opened.length
        client = new ScopeCoordinator()
        client.onStart()
        for (let attempt = 0; attempt < 250 && FakeWebSocket.opened.length === before; attempt++) await delay(20)
        if (FakeWebSocket.opened.length === before) throw new Error('the client opened no socket')
        socket = FakeWebSocket.opened[FakeWebSocket.opened.length - 1]
        seen = 0
      } else if (step.expectSent) {
        matcher.match(step.expectSent, await nextSent())
      } else if (step.expectNothingSent) {
        await delay(500)
        while (socket && seen < socket.sent.length) {
          const message = withoutKeepAlives(JSON.parse(socket.sent[seen++]))
          if (message) throw new Error(`expected nothing sent, got ${JSON.stringify(message)}`)
        }
      } else if (step.receive) {
        const message = await withServerKey(matcher.substitute(step.receive) as Record<string, unknown>)
        socket!.onmessage!(new MessageEvent('message', { data: JSON.stringify(message) }))
        await delay(50)
      } else if (step.setField) {
        client!.setFormField(step.setField.vsid, step.setField.name, step.setField.value)
      } else if (step.fire) {
        client!.submit(step.fire.vsid, step.fire.event)
      } else if (step.expectScope) {
        const vsid = matcher.substitute(step.expectScope.vsid) as string
        const binding = client!.viewMap.get(vsid)
        if (!binding) throw new Error(`no view ${vsid}`)
        matcher.match(step.expectScope.scope, binding.getScope())
      } else if (step.expectNoView) {
        const vsid = matcher.substitute(step.expectNoView) as string
        if (client!.viewMap.has(vsid)) throw new Error(`the view ${vsid} is still there`)
      } else if (step.expectOutdated !== undefined) {
        if (client!.outdated !== step.expectOutdated) throw new Error(`expected outdated = ${step.expectOutdated}`)
      } else {
        throw new Error(`unknown step ${JSON.stringify(step)}`)
      }
    } catch (e) {
      throw new Error(`step ${index + 1}: ${(e as Error).message}`)
    }
  }
  client?.onStop()
}

describe('client conformance fixtures', () => {
  let realWebSocket: typeof WebSocket

  beforeEach(() => {
    realWebSocket = globalThis.WebSocket
    globalThis.WebSocket = FakeWebSocket as unknown as typeof WebSocket
  })

  afterEach(() => {
    globalThis.WebSocket = realWebSocket
  })

  const files = readdirSync(FIXTURES)
    .filter((it) => it.endsWith('.json'))
    .sort()
  it('has fixtures', () => {
    if (files.length === 0) throw new Error(`no client fixtures in ${FIXTURES}`)
  })
  for (const file of files) {
    it(file, async () => {
      await runFixture(JSON.parse(readFileSync(join(FIXTURES, file), 'utf8')))
    })
  }
})
