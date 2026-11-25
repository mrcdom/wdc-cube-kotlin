// Public entry of @wdc/remote-bridge-react: the React bindings of @wdc/remote-bridge.
export { default } from './Bridge'
export type { IViewFactory, ViewComponent, ViewProps } from './types'
export { BaseViewClass, BasePanelClass } from './ViewClasses'

// What a React shell needs from the protocol's client, so it imports a single package.
export { BROWSER_VID, ViewBinding } from '@wdc/remote-bridge'
export type { BrowserScope } from '@wdc/remote-bridge'
