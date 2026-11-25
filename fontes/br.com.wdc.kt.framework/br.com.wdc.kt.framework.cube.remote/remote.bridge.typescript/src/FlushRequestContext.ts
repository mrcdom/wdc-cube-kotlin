import type { ScopeCoordinator } from './ScopeCoordinator'
import type { BrowserScope, FormMapType } from './types'
import { BROWSER_VSID, CAUTHED, KEEP_ALIVE_INTERVAL, PROTOCOL_VERSION } from './constants'

export class FlushRequestContext {
  private readonly app: ScopeCoordinator
  private readonly onVisibilityOrFocus = () => {
    if (!document.hidden) {
      this.keepAliveNow()
    }
  }

  socket: WebSocket | null
  /**
   * Whether the hello (with the handshake's `clientKey`) went out on the current socket, and whether the host answered
   * it. Requests wait for the answer, which says up to which request the host already has: sent before it, they could
   * reach a host that has not taken the handshake, or go twice.
   */
  private helloSent = false
  private helloAnswered = false
  requestMap = new Map<number, FormMapType>()
  lastSentRequestId = -1
  requestCount = 0
  lastProcessedId = -1
  keepAliveHandler = 0
  pendingKeepAlive = 0
  private submittingTimer = 0
  private submittingTimeout = 0
  private userRequestIds = new Set<number>()

  constructor(app: ScopeCoordinator) {
    this.app = app
    this.socket = null

    document.addEventListener('visibilitychange', this.onVisibilityOrFocus)
    window.addEventListener('focus', this.onVisibilityOrFocus)
  }

  submit(formMap: FormMapType, vsid: string, eventId: number, silent = false) {
    this.cancelPendingKeepAlive()

    formMap.requestId = this.requestCount++
    if (!formMap.event) {
      formMap.event = [vsid + ':' + eventId]
    } else {
      formMap.event.push(vsid + ':' + eventId)
    }
    this.requestMap.set(formMap.requestId, formMap)

    if (!silent) {
      this.userRequestIds.add(formMap.requestId)
    }

    this.resetKeepAliveTimer()
    this.flush()
  }

  flush() {
    type RequestObjType = {
      requestId: number
      event: string[]
    }

    const { socket, lastSentRequestId, requestCount, requestMap } = this

    if (socket && socket.readyState === WebSocket.OPEN && this.helloAnswered) {
      const requestObj: Partial<RequestObjType> & Record<string, unknown> = {
        event: [],
      }
      let hasData = false
      for (let i = lastSentRequestId + 1; i < requestCount; i++) {
        const requestItemObj = requestMap.get(i)
        if (!requestItemObj) {
          continue
        }

        const keys = Object.keys(requestItemObj)
        for (let j = 0; j < keys.length; j++) {
          const key = keys[j]
          const value = requestItemObj[key] as unknown
          if (value) {
            if ('event' === key) {
              const valueArray = value as string[]
              for (let k = 0; k < valueArray.length; k++) {
                requestObj.event!.push(valueArray[k])
              }
            } else {
              let formData = requestObj[key] as object
              if (!formData) {
                formData = {}
                requestObj[key] = formData
              }
              Object.assign(formData, value as object)
            }
          }
        }
        requestObj.requestId = i

        this.lastSentRequestId = i

        hasData = true
      }

      if (hasData) {
        socket.send(JSON.stringify(requestObj))
        if (this.userRequestIds.size > 0) {
          this.setSubmitting(true)
        }
      }
    }
  }

  open(url: string) {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return
    }

    const me = this
    const app = this.app

    this.helloSent = false
    this.helloAnswered = false
    const socket = (this.socket = new WebSocket(url, ['wdc']))
    ;(socket as unknown as Record<string, unknown>).withCredentials = true

    const handleDisconnect = (cause: unknown) => {
      this.socket = null
      this.helloSent = false
      this.helloAnswered = false
      app.isConnected = false
      this.stopKeepAliveChecks()
      this.userRequestIds.clear()
      this.setSubmitting(false)
      // an out-of-date page does not come back by reconnecting
      if (!app.outdated) {
        app.reconnectController.reconnect(cause)
      }
    }

    socket.onopen = () => {
      app.isConnected = true
      this.initKeepAliveChecks()
      this.keepAliveNow()

      // Each socket agrees on its own key: the hello carries a new public key, the answer brings the host's.
      const sendHello = (clientKey?: string) => {
        if (this.socket !== socket || socket.readyState !== WebSocket.OPEN) {
          return
        }
        socket.send(
          JSON.stringify({
            ping: true,
            requestId: this.lastProcessedId,
            path: app.path,
            clientKey,
            protocol: PROTOCOL_VERSION,
          }),
        )
        // The queued requests go with the answer, from the last one the host processed.
        this.helloSent = true
      }

      app.dataSecurity
        .startHandshake()
        .then(sendHello)
        .catch((error) => {
          // Without Web Crypto (an insecure context, for instance), the session goes on with nothing to cipher with.
          CAUTHED(error)
          sendHello()
        })
    }

    socket.onerror = (error) => {
      handleDisconnect(error)
    }

    socket.onclose = (event: CloseEvent) => {
      // Server sent close code 4001: session is invalid, reload the page
      if (event.code === 4001) {
        window.location.reload()
        return
      }
      handleDisconnect(event)
    }

    // Log messages from the server
    socket.onmessage = (e) => {
      if (app.reconnectController.count > 0) {
        app.reconnectController.reset()
      }
      const response = JSON.parse(e.data)

      // the host does not speak this client's version (`protocols` says which it does): the page is out of date
      if (response.error === 'unsupported-protocol') {
        console.warn(
          `Remote bridge: the host speaks protocol ${JSON.stringify(response.protocols)}, this page ${PROTOCOL_VERSION}`,
        )
        app.markOutdated()
        me.stopKeepAliveChecks()
        me.setSubmitting(false)
        socket.close()
        return
      }

      // the first message on this socket answers the hello: the requests that waited can go
      if (me.helloSent) {
        me.helloAnswered = true
      }

      if (response.ping) {
        me.lastSentRequestId = response.requestId
        me.lastProcessedId = response.requestId
      }

      for (let i = me.lastProcessedId + 1; i <= response.requestId; i++) {
        me.requestMap.delete(i)
        me.userRequestIds.delete(i)
        me.lastProcessedId = i
      }

      if (response.serverKey) {
        app.dataSecurity.completeHandshake(response.serverKey, app.id).catch(CAUTHED)
      }

      if (response.uri) {
        app.path = response.uri
        window.location.href = `#${response.uri}`
      }

      if (response.scopes) {
        app.applyScopes(response.scopes)
      }

      // after the scopes that let go of them
      if (response.releasedViews) {
        app.viewGarbageCollector.released(response.releasedViews)
      }

      me.flush()
      me.setSubmitting(me.userRequestIds.size > 0)
    }
  }

  close() {
    if (this.socket) {
      this.socket.close()
      this.socket = null
    }
  }

  private initKeepAliveChecks() {
    this.stopKeepAliveChecks()
    this.keepAliveHandler = window.setTimeout(this.keepAlive, KEEP_ALIVE_INTERVAL)
  }

  private stopKeepAliveChecks() {
    window.clearTimeout(this.keepAliveHandler)
    this.keepAliveHandler = 0
  }

  private keepAliveNow() {
    this.cancelPendingKeepAlive()
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.pendingKeepAlive = window.setTimeout(() => {
        this.pendingKeepAlive = 0
        this.app.onKeepAlive()
      }, 80)
    }
  }

  private cancelPendingKeepAlive() {
    if (this.pendingKeepAlive !== 0) {
      window.clearTimeout(this.pendingKeepAlive)
      this.pendingKeepAlive = 0
    }
  }

  private resetKeepAliveTimer() {
    if (this.keepAliveHandler !== 0) {
      this.stopKeepAliveChecks()
      this.keepAliveHandler = window.setTimeout(this.keepAlive, KEEP_ALIVE_INTERVAL)
    }
  }

  private setSubmitting(value: boolean) {
    if (value) {
      if (this.submittingTimer === 0) {
        this.submittingTimer = window.setTimeout(() => {
          this.submittingTimer = 0
          this.applySubmitting(true)
        }, 200)
      }
      if (this.submittingTimeout === 0) {
        this.submittingTimeout = window.setTimeout(() => {
          this.submittingTimeout = 0
          this.setSubmitting(false)
        }, 15_000)
      }
    } else {
      if (this.submittingTimer !== 0) {
        window.clearTimeout(this.submittingTimer)
        this.submittingTimer = 0
      }
      if (this.submittingTimeout !== 0) {
        window.clearTimeout(this.submittingTimeout)
        this.submittingTimeout = 0
      }
      this.applySubmitting(false)
    }
  }

  private applySubmitting(value: boolean) {
    const binding = this.app.viewMap.get(BROWSER_VSID)
    if (binding) {
      const scope = binding.getScope() as BrowserScope
      if (scope.submitting !== value) {
        scope.submitting = value
        binding.forceUpdate()
      }
    }
  }

  private readonly keepAlive = () => {
    this.stopKeepAliveChecks()
    this.keepAliveNow()
    this.keepAliveHandler = window.setTimeout(this.keepAlive, KEEP_ALIVE_INTERVAL)
  }
}
