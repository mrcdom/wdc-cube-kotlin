import React from 'react'
import * as history from 'history'
import * as LangUtils from '@root/utils/LangUtils'
import CookieConstructor from 'universal-cookie'

import type { FormMapType, IViewFactory } from './types'
import { NOOP_VOID, NOOP_PROMISE_VOID, CAUTHED, BROWSER_VSID } from './constants'
import { ViewBinding } from './ViewBinding'
import { DataSecurity } from './DataSecurity'
import { FlushRequestContext } from './FlushRequestContext'
import { ReconnectController } from './ReconnectController'
import { ViewGarbageCollector } from './ViewGarbageCollector'

const Cookie = new CookieConstructor()

export class Application {
  readonly id: string
  readonly history = history.createHashHistory()

  readonly viewFactory = new Map<string, IViewFactory>()
  readonly viewMap = new Map<string, ViewBinding>()

  formMap: FormMapType = {}

  isConnected = false
  path = ''
  baseWebSocketUtl = ''
  unlistenHistory = NOOP_VOID

  readonly dataSecurity = new DataSecurity()
  readonly contextExchanger: FlushRequestContext
  readonly reconnectController: ReconnectController
  readonly viewGarbageCollector: ViewGarbageCollector

  readyToStart = NOOP_PROMISE_VOID

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

    const appSKey = Cookie.get('app_skey')
    if (appSKey) {
      this.dataSecurity.updateSecurityKey(appSKey)
      Cookie.remove('app_skey')

      this.readyToStart = async () => {
        try {
          await this.dataSecurity.updateSecretWithRandomPassword()

          Cookie.set('app_signature', this.dataSecurity.getSignature())
          this.readyToStart = NOOP_PROMISE_VOID
        } catch (error) {
          CAUTHED(error)
        }
      }
    }

    const contextPath = location.pathname.split('/')[1]

    this.baseWebSocketUtl =
      (document.location.protocol === 'http:' ? 'ws://' : 'wss://') +
      document.location.host +
      '/' +
      (contextPath ?? 'unknown')

    const slashPos = this.baseWebSocketUtl.indexOf('/', 10)
    if (slashPos != -1) {
      this.baseWebSocketUtl = this.baseWebSocketUtl.substring(0, slashPos)
    }

    this.contextExchanger = new FlushRequestContext(this)
    this.reconnectController = new ReconnectController(this)
    this.viewGarbageCollector = new ViewGarbageCollector(this)
  }

  getBaseWebSocketUrl() {
    return this.baseWebSocketUtl
  }

  registerView(viewId: string, factory: IViewFactory) {
    this.viewFactory.set(viewId, factory)
  }

  createView(vsid: string, props?: Record<string, unknown>) {
    const parts = vsid.split(/:/g)
    const viewCreator = this.viewFactory.get(parts[0])
    if (!viewCreator) {
      throw new Error(`No view registered for viewId: "${parts[0]}"`)
    }

    let binding = this.viewMap.get(vsid)
    if (!binding) {
      binding = new ViewBinding(vsid)
      this.viewMap.set(vsid, binding)
    }

    return viewCreator(vsid, props ?? {})
  }

  connect() {
    this.reconnectController.checkNow()
  }

  bindView<T>(vsid: string) {
    let binding = this.viewMap.get(vsid) ?? null
    if (!binding) {
      binding = this.viewGarbageCollector.recover(vsid)
      if (!binding) {
        throw new Error(`Missing View Scope for id(${vsid})`)
      }
    }

    const [updateCount, setUpdateCount] = React.useState(0)
    binding.forceUpdate = () => setUpdateCount((count) => count + 1)

    React.useEffect(() => {
      // attached
      return () => {
        // detached
        this.viewGarbageCollector.mark(binding)
      }
    }, [])

    return { scope: binding.getScope() as T, binding }
  }

  holdView(vsid: string | undefined) {
    React.useEffect(() => {
      if (vsid) {
        this.viewGarbageCollector.hold(vsid)
      }
      return () => {
        if (vsid) {
          this.viewGarbageCollector.release(vsid)
        }
      }
    }, [vsid])
  }

  onStart() {
    const action = async () => {
      // Wait construction async initialization
      await this.readyToStart()

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

      let binding = this.viewMap.get(vsid)
      if (!binding) {
        binding = new ViewBinding(vsid)
        this.viewMap.set(vsid, binding)
      }

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
