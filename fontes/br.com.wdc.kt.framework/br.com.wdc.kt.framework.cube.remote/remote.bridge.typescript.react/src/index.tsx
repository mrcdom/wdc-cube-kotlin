// Public entry of wdc-cube-remote-bridge-react: the React bindings of wdc-cube-remote-bridge.
export { default } from './Bridge.js'
export type { IViewFactory, ViewComponent, ViewProps } from './types.js'
export { BaseViewClass, BasePanelClass } from './ViewClasses.js'

// What a React shell needs from the protocol's client, so it imports a single package.
export { BROWSER_VID, ViewBinding } from 'wdc-cube-remote-bridge'
export type { BrowserScope } from 'wdc-cube-remote-bridge'
