# remote-bridge-react

React bindings for the TypeScript client of the **remote Cube MVP** protocol, [`wdc-cube-remote-bridge`](../remote.bridge.typescript/). The client does the protocol — WebSocket, security handshake, scopes, history, events — and draws nothing; this package draws its views with React: which component paints each view id, the hooks that bind a component to its scope, and base classes for views written as classes. The protocol itself (messages, events, security) is described in the [client's README](../remote.bridge.typescript/README.md).

It knows nothing about any application: which views exist, how they look and what they say belong to the React shell that consumes it (in this repository, the WDC Store client, [`remote.react.typescript`](../../../br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript/)). The Kotlin/JS counterpart is [`framework-cube-remote-bridge-react`](../remote.bridge.kotlin.react/).

The package name is `wdc-cube-remote-bridge-react`, to be published on npm with 0.5.0 (not published yet):

```bash
npm install wdc-cube-remote-bridge-react wdc-cube-remote-bridge react history universal-cookie
```

It ships as ES modules with their types; its peers are `react` and `wdc-cube-remote-bridge`. Importing it does nothing by itself: the bridge object makes its coordinator on first use, in the browser. In this repository, the WDC Store client imports it from source instead, through a bundler alias (below).

## Structure

```
src/
  index.tsx        # Public entry: the bridge object, the classes and types, and what a shell needs from the client
  Bridge.tsx       # The bridge object (default export): the API views and shells call; makes the coordinator on first use
  ReactViews.ts    # The view factories by view id, and the hooks behind bindView and holdView
  ViewClasses.tsx  # BaseViewClass and BasePanelClass: views and panels written as classes
  types.ts         # ViewProps, ViewComponent, IViewFactory
```

## Integrating it in a React shell

From source (as the WDC Store client does), the package's own `node_modules` are only for building and testing it. Its runtime dependencies are **peer dependencies**: `react`, and the protocol client `wdc-cube-remote-bridge`, whose own peers are `history` and `universal-cookie`. All of them resolve in the shell. Because the packages' files live outside the shell's folder, both the bundler and TypeScript must be told where to find them and where their peer dependencies are.

`package.json` of the shell (Parcel):

```json
"alias": {
  "wdc-cube-remote-bridge-react": "<path to this folder>/src/index.tsx",
  "wdc-cube-remote-bridge": "<path to remote.bridge.typescript>/src/index.ts",
  "react": "./node_modules/react",
  "history": "./node_modules/history",
  "universal-cookie": "./node_modules/universal-cookie"
}
```

`tsconfig.json` of the shell:

```jsonc
"paths": {
  "wdc-cube-remote-bridge-react": ["<path to this folder>/src/index.tsx"],
  "wdc-cube-remote-bridge": ["<path to remote.bridge.typescript>/src/index.ts"],
  "react": ["./node_modules/@types/react"],
  "history": ["./node_modules/history"],
  "universal-cookie": ["./node_modules/universal-cookie"]
}
```

The peer aliases also make sure the bindings and the shell share one copy of React. The shell imports only `wdc-cube-remote-bridge-react`: it re-exports what a shell needs from the client.

Bootstrap: register the shell's views and render the browser view, the root of every screen.

```tsx
import bridge from 'wdc-cube-remote-bridge-react'

bridge.registerComponents(BrowserView, LoginView, CartView /* , ... */)

root.render(bridge.createBrowserView())
```

The shell supplies the browser view itself (registered under `BROWSER_VID`): it calls `bridge.onStart()` when mounted and `bridge.onStop()` when unmounted, draws `scope.contentViewId`, and shows the alert, the reconnection state (`scope.error`) and the activity indicator (`scope.submitting`) as it likes.

A view is a class turned into a function component, registered under the view id of the scope it draws on the server:

```tsx
import bridge, { BaseViewClass, type ViewProps } from 'wdc-cube-remote-bridge-react'

class LoginViewClass extends BaseViewClass<ViewProps, LoginScope> {
  override render({ className }: ViewProps) {
    const { scope } = this
    // ... draw the scope; on submit:
    // bridge.setFormField(this.vsid, 'userName', userName)
    // bridge.setFormField(this.vsid, 'password', await bridge.cipher(password))
    // bridge.submit(this.vsid, ON_ENTER)
  }
}

export default BaseViewClass.FC(LoginViewClass, 'c677cda52d14')
```

## API

The default export, `bridge`:

| Method | What it does |
|---|---|
| `registerComponents(...components)` / `registerComponent(component)` | Registers views by their `VIEW_ID`, memoized: a parent's repaint does not repaint its children |
| `registerView(viewId, factory)` | Registers a view with a factory of your own |
| `createBrowserView()` | The root element: the browser view, instance 0 |
| `createView(vsid, props?)` | The element of a view the server referenced (`null` for an empty id) |
| `bindView<T>(vsid)` | Hook: binds a function component to its scope; returns `{ scope, binding }` |
| `holdView(vsid)` | Hook: keeps a view the component does not draw from being collected |
| `setFormField(vsid, name, value)` | Queues a value for the next event of that view |
| `submit(vsid, eventId)` | Sends an event; shows the activity indicator if it takes long |
| `submitSilent(vsid, eventId)` | Sends an event without the activity indicator |
| `cipher(text)` | Encrypts a sensitive value with the key of the current socket (ECDH handshake, AES-GCM, Base64, a new random IV per value); waits for the handshake if it has not completed |
| `setOutdatedMessage(text)` | The text shown in the browser's alert when the host refuses the protocol version (English by default) |
| `connect()` | Reconnects now, without waiting for the backoff |
| `onStart()` / `onStop()` | Opens the session and listens to the history / stops listening |
| `getScope<T>(vsid)` / `getBaseUrl()` | The current scope of a view / the WebSocket base URL |

Also exported: `BaseViewClass`, `BasePanelClass` and the types `ViewProps`, `ViewComponent` and `IViewFactory`; and, re-exported from `wdc-cube-remote-bridge`, `BROWSER_VID`, `ViewBinding` and the type `BrowserScope`.

`bindView` sets the binding's `forceUpdate` to repaint the component and tells the client's collector when the view enters and leaves the screen; `holdView` holds and releases a view through the same collector. Everything else delegates to the client's `ScopeCoordinator`, of which the bridge creates one per page.
