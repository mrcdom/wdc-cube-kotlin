import React from 'react'
import { Application } from './app/Application'
import { BROWSER_VSID } from './app/constants'
import type { IViewFactory, ViewComponent } from './app/types'

export { BROWSER_VID } from './app/constants'
export type { BrowserScope, ViewComponent, ViewProps } from './app/types'
export { ViewBinding } from './app/ViewBinding'

async function static_updateAllScopes(app: Application, vsids: string[]) {
  var url = `view-scope`

  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Application-Id': app.id,
    },
    body: JSON.stringify(vsids),
  })

  const scopes = (await resp.json()) as { id: string }[]
  app.applyScopes(scopes)
}

const privateApp = new Application()

const publicApp = new (class {
  getBaseUrl() {
    return privateApp.baseWebSocketUtl
  }

  getScope<T>(vsid: string) {
    var binding = privateApp.viewMap.get(vsid)
    return binding ? (binding.getScope() as T) : ({} as T)
  }

  connect() {
    privateApp.connect()
  }

  submit(vsid: string, eventId: number) {
    privateApp.submit(vsid, eventId)
  }

  submitSilent(vsid: string, eventId: number) {
    privateApp.submitSilent(vsid, eventId)
  }

  async cipher(value: string) {
    return privateApp.dataSecurity.b64Cipher(value)
  }

  setFormField(vsid: string, fieldName: string, fieldValue: unknown) {
    privateApp.setFormField(vsid, fieldName, fieldValue)
  }

  registerView(viewId: string, factory: IViewFactory) {
    privateApp.registerView(viewId, factory)
  }

  registerComponent(Component: ViewComponent) {
    // Memoized: a parent repaint does not repaint the child views. Cube views are independent; each one repaints
    // itself when its own state arrives (bindView's forceUpdate), and a parent only repaints a child it swaps.
    const View = React.memo(Component)
    privateApp.registerView(Component.VIEW_ID, (vsid, props) => <View vsid={vsid} {...props} />)
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
    return privateApp.createView(vsid, props)
  }

  createBrowserView() {
    return privateApp.createView(BROWSER_VSID)
  }

  bindView<T>(vsid: string) {
    return privateApp.bindView<T>(vsid)
  }

  holdView(vsid: string | undefined) {
    privateApp.holdView(vsid)
  }

  updateScope(vsid: string) {
    static_updateAllScopes(privateApp, [vsid])
  }

  onStart() {
    privateApp.onStart()
  }

  onStop() {
    privateApp.onStop()
  }
})()

export default publicApp
