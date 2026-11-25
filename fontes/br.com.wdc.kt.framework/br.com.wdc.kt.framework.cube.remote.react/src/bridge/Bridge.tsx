import React from 'react'
import { ScopeCoordinator } from './ScopeCoordinator'
import { BROWSER_VSID } from './constants'
import type { IViewFactory, ViewComponent } from './types'

const coordinator = new ScopeCoordinator()

/** The bridge's public API: what a React shell and its views use to render scopes and send events. */
const bridge = new (class {
  getBaseUrl() {
    return coordinator.baseWebSocketUrl
  }

  getScope<T>(vsid: string) {
    var binding = coordinator.viewMap.get(vsid)
    return binding ? (binding.getScope() as T) : ({} as T)
  }

  connect() {
    coordinator.connect()
  }

  submit(vsid: string, eventId: number) {
    coordinator.submit(vsid, eventId)
  }

  submitSilent(vsid: string, eventId: number) {
    coordinator.submitSilent(vsid, eventId)
  }

  async cipher(value: string) {
    return coordinator.dataSecurity.b64Cipher(value)
  }

  setFormField(vsid: string, fieldName: string, fieldValue: unknown) {
    coordinator.setFormField(vsid, fieldName, fieldValue)
  }

  registerView(viewId: string, factory: IViewFactory) {
    coordinator.registerView(viewId, factory)
  }

  registerComponent(Component: ViewComponent) {
    // Memoized: a parent repaint does not repaint the child views. Cube views are independent; each one repaints
    // itself when its own state arrives (bindView's forceUpdate), and a parent only repaints a child it swaps.
    const View = React.memo(Component)
    coordinator.registerView(Component.VIEW_ID, (vsid, props) => <View vsid={vsid} {...props} />)
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
    return coordinator.createView(vsid, props)
  }

  createBrowserView() {
    return coordinator.createView(BROWSER_VSID)
  }

  bindView<T>(vsid: string) {
    return coordinator.bindView<T>(vsid)
  }

  holdView(vsid: string | undefined) {
    coordinator.holdView(vsid)
  }

  onStart() {
    coordinator.onStart()
  }

  onStop() {
    coordinator.onStop()
  }
})()

export default bridge
