import * as history from 'history'
import * as LangUtils from './utils/LangUtils'
import CookieConstructor from 'universal-cookie'

import type { FormMapType } from './types'
import { NOOP_VOID, CAUTHED, BROWSER_VSID } from './constants'
import { ViewBinding } from './ViewBinding'
import { DataSecurity } from './DataSecurity'
import { FlushRequestContext } from './FlushRequestContext'
import { ReconnectController } from './ReconnectController'
import { ViewGarbageCollector } from './ViewGarbageCollector'

const Cookie = new CookieConstructor()

/**
 * The client side of one remote application: the session id, the security handshake, the WebSocket exchange, the
 * history and the bindings of the views the server publishes.
 *
 * It knows no UI technology. A technology binding (such as `@wdc/remote-bridge-react`) creates one coordinator, draws
 * each view from its binding (`viewBinding`, `findView`), sets `ViewBinding.forceUpdate` to repaint it, and tells the
 * collector when a view enters and leaves the screen (`viewGarbageCollector.mount`/`mark`) and which views it keeps
 * alive without drawing them (`hold`/`release`).
 */
export class ScopeCoordinator {
  readonly id: string
  readonly history = history.createHashHistory()

  /** The bindings of the views alive on this client, by vsid. */
  readonly viewMap = new Map<string, ViewBinding>()

  formMap: FormMapType = {}

  isConnected = false
  path = ''
  baseWebSocketUrl = ''
  unlistenHistory = NOOP_VOID

  readonly dataSecurity = new DataSecurity()
  readonly contextExchanger: FlushRequestContext
  readonly reconnectController: ReconnectController
  /** Public: the technology binding reports views entering and leaving the screen, and holds views. */
  readonly viewGarbageCollector: ViewGarbageCollector

  constructor() {
    this.viewMap.set(BROWSER_VSID, new ViewBinding(BROWSER_VSID))

    const appIdFromCookie = Cookie.get('app_id')
    if (appIdFromCookie) {
      Cookie.remove('app_id')
    }

    let appId = sessionStorage.getItem('app_id')
    if (!appId) {
      appId = appIdFromCookie
      if (appId) {
        sessionStorage.setItem('app_id', appId)
      } else {
        appId = LangUtils.makeUniqueId() + '.fake'
      }
    }

    this.id = appId

    const contextPath = location.pathname.split('/')[1]

    this.baseWebSocketUrl =
      (document.location.protocol === 'http:' ? 'ws://' : 'wss://') +
      document.location.host +
      '/' +
      (contextPath ?? 'unknown')

    const slashPos = this.baseWebSocketUrl.indexOf('/', 10)
    if (slashPos != -1) {
      this.baseWebSocketUrl = this.baseWebSocketUrl.substring(0, slashPos)
    }

    this.contextExchanger = new FlushRequestContext(this)
    this.reconnectController = new ReconnectController(this)
    this.viewGarbageCollector = new ViewGarbageCollector(this)
  }

  getBaseWebSocketUrl() {
    return this.baseWebSocketUrl
  }

  /** The binding of a view, created empty if the server has not published its scope yet. */
  viewBinding(vsid: string) {
    let binding = this.viewMap.get(vsid)
    if (!binding) {
      binding = new ViewBinding(vsid)
      this.viewMap.set(vsid, binding)
    }
    return binding
  }

  /** The binding of a live view, recovered from the collector if it was about to be dropped; `null` if gone. */
  findView(vsid: string) {
    return this.viewMap.get(vsid) ?? this.viewGarbageCollector.recover(vsid)
  }

  connect() {
    this.reconnectController.checkNow()
  }

  onStart() {
    const action = async () => {
      this.assureContextExchangerIsConnected()

      this.unlistenHistory()
      this.unlistenHistory = this.history.listen(({ action, location }) => {
        if (action === 'POP') {
          let path = `${location.pathname}${location.search ? location.search : ''}`
          if (this.path !== path) {
            this.onHistoryChange(path)
          }
        }
      })

      const hash = window.location.hash
      this.path = hash && hash.length > 1 ? hash.substring(1) : '/'

      this.setFormField(BROWSER_VSID, 'p.path', this.path)
      this.submit(BROWSER_VSID, -1)
    }

    action().catch(CAUTHED)
  }

  onStop() {
    this.unlistenHistory()
    this.unlistenHistory = NOOP_VOID
  }

  onHistoryChange(path: string) {
    this.setFormField(BROWSER_VSID, 'p.path', path)
    this.submit(BROWSER_VSID, -2)
  }

  onKeepAlive() {
    this.submit(BROWSER_VSID, 2)
  }

  applyScopes(scopeList: { id: string }[]) {
    for (let i = 0, ilen = scopeList.length; i < ilen; i++) {
      let scope = scopeList[i]
      if (!scope || !scope.id) {
        continue
      }
      const vsid = scope.id
      this.viewGarbageCollector.recover(vsid)

      const binding = this.viewBinding(vsid)
      binding.setScope(scope)
      this.viewGarbageCollector.updateAutoHolds(binding)
    }
  }

  submit(vsid: string, eventId: number) {
    const oldFormMap = this.formMap
    this.formMap = {}
    const silent = vsid === BROWSER_VSID
    this.contextExchanger.submit(oldFormMap, vsid, eventId, silent)
  }

  submitSilent(vsid: string, eventId: number) {
    const oldFormMap = this.formMap
    this.formMap = {}
    this.contextExchanger.submit(oldFormMap, vsid, eventId, true)
  }

  setFormField(vsid: string, fieldName: string, fieldValue: unknown) {
    var formData = this.formMap[vsid] as Record<string, unknown>
    if (!formData) {
      formData = {}
      this.formMap[vsid] = formData
    }
    formData[fieldName] = fieldValue
  }

  readonly assureContextExchangerIsConnected = () => {
    this.contextExchanger.open(this.reconnectController.url)
  }
}
