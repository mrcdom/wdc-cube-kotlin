import React from 'react'
import { ScopeCoordinator, BROWSER_VSID } from 'wdc-cube-remote-bridge'
import { ReactViews } from './ReactViews.js'
import type { IViewFactory, ViewComponent } from './types.js'

let instance: { coordinator: ScopeCoordinator; views: ReactViews } | null = null

/**
 * The page's coordinator and view registry, made on first use: importing the package does nothing (it can be imported
 * where there is no browser, as in server rendering or tests).
 */
function state() {
  if (!instance) {
    const coordinator = new ScopeCoordinator()
    instance = { coordinator, views: new ReactViews(coordinator) }
  }
  return instance
}

const coordinator = () => state().coordinator
const views = () => state().views

/** The bridge's public API: what a React shell and its views use to render scopes and send events. */
const bridge = new (class {
  getBaseUrl() {
    return coordinator().baseWebSocketUrl
  }

  getScope<T>(vsid: string) {
    const binding = coordinator().viewMap.get(vsid)
    return binding ? (binding.getScope() as T) : ({} as T)
  }

  connect() {
    coordinator().connect()
  }

  submit(vsid: string, eventId: number) {
    coordinator().submit(vsid, eventId)
  }

  submitSilent(vsid: string, eventId: number) {
    coordinator().submitSilent(vsid, eventId)
  }

  async cipher(value: string) {
    return coordinator().dataSecurity.b64Cipher(value)
  }

  setFormField(vsid: string, fieldName: string, fieldValue: unknown) {
    coordinator().setFormField(vsid, fieldName, fieldValue)
  }

  registerView(viewId: string, factory: IViewFactory) {
    views().register(viewId, factory)
  }

  registerComponent(Component: ViewComponent) {
    // Memoized: a parent repaint does not repaint the child views(). Cube views are independent; each one repaints
    // itself when its own state arrives (bindView's forceUpdate), and a parent only repaints a child it swaps.
    const View = React.memo(Component)
    views().register(Component.VIEW_ID, (vsid, props) => <View vsid={vsid} {...props} />)
  }

  registerComponents(...components: ViewComponent[]) {
    for (const Component of components) {
      this.registerComponent(Component)
    }
  }

  createView(vsid: string | null | undefined, props?: Record<string, unknown>) {
    if (!vsid) {
      return null
    }
    return views().create(vsid, props)
  }

  createBrowserView() {
    return views().create(BROWSER_VSID)
  }

  bindView<T>(vsid: string) {
    return views().useBinding<T>(vsid)
  }

  holdView(vsid: string | undefined) {
    views().useHold(vsid)
  }

  onStart() {
    coordinator().onStart()
  }

  onStop() {
    coordinator().onStop()
  }
})()

export default bridge
