# remote-bridge-react

TypeScript/React bridge for the **remote Cube MVP** protocol: the browser side of the Kotlin module [`framework-cube-remote`](../br.com.wdc.kt.framework.cube.remote/). The presenters run on the server; this package connects to it over WebSocket, does the security handshake, keeps the scopes the server publishes (each presenter's view state, serialized) and gives the React views a small API to draw them and send events back.

It knows nothing about any application: which views exist, how they look and what they say belong to the React shell that consumes it (in this repository, the WDC Store client, [`remote.react.typescript`](../../br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript/)).

The package name is `@wdc/remote-bridge-react`. It is not published to npm: a shell imports it from source, through a bundler alias.

## Structure

```
src/
  bridge/
    index.tsx               # Public entry: the bridge object and the exported types and classes
    Bridge.tsx              # The bridge object (default export): the API views and shells call
    ScopeCoordinator.ts     # Session id, handshake, history, view registry and bindings, form fields
    FlushRequestContext.ts  # The WebSocket: batches events and form fields, applies scopes and releasedViews,
                            # keep-alive, the "submitting" flag
    ReconnectController.ts  # Reconnection with linear backoff: min(2000·n, 120000) ms
    DataSecurity.ts         # Handshake (textbook RSA + PBKDF2) and AES-GCM for sensitive fields
    ViewBinding.ts          # Holds one view's scope; repaints the view when a new scope arrives
    ViewGarbageCollector.ts # Drops views the server released or that left the screen
    ViewClasses.tsx         # BaseViewClass and BasePanelClass: views and panels written as classes
    types.ts                # BrowserScope, ViewProps, ViewComponent, ...
    constants.ts            # Browser view id, keep-alive interval
  utils/
    RSA.ts                  # Textbook RSA (modular exponentiation, no padding) over BigInt
    BigIntUtils.ts          # BigInt parsing and conversion from bytes
    Base64.ts               # Base64 and URL-safe Base64
    UTF8.ts                 # TextEncoder/TextDecoder helpers
    LangUtils.ts            # Small helpers (unique id, clearing an object)
```

## Integrating it in a React shell

The bridge has no `node_modules` of its own. Its runtime libraries are **peer dependencies** (`react`, `history`, `universal-cookie`), installed in the shell. Because the bridge's files live outside the shell's folder, both the bundler and TypeScript must be told where to find the bridge and where its peer dependencies are.

`package.json` of the shell (Parcel):

```json
"alias": {
  "@wdc/remote-bridge-react": "<path to this folder>/src/bridge/index.tsx",
  "react": "./node_modules/react",
  "history": "./node_modules/history",
  "universal-cookie": "./node_modules/universal-cookie"
}
```

`tsconfig.json` of the shell:

```jsonc
"paths": {
  "@wdc/remote-bridge-react": ["<path to this folder>/src/bridge/index.tsx"],
  "react": ["./node_modules/@types/react"],
  "history": ["./node_modules/history"],
  "universal-cookie": ["./node_modules/universal-cookie"]
}
```

The peer aliases also make sure the bridge and the shell share one copy of React.

Bootstrap: register the shell's views and render the browser view, the root of every screen.

```tsx
import bridge from '@wdc/remote-bridge-react'

bridge.registerComponents(BrowserView, LoginView, CartView /* , ... */)

root.render(bridge.createBrowserView())
```

The shell supplies the browser view itself (registered under `BROWSER_VID`): it calls `bridge.onStart()` when mounted and `bridge.onStop()` when unmounted, draws `scope.contentViewId`, and shows the alert, the reconnection state (`scope.error`) and the activity indicator (`scope.submitting`) as it likes.

A view is a class turned into a function component, registered under the view id of the scope it draws on the server:

```tsx
import bridge, { BaseViewClass, type ViewProps } from '@wdc/remote-bridge-react'

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
| `cipher(text)` | Encrypts a sensitive value with the session key (AES-GCM, Base64) |
| `connect()` | Reconnects now, without waiting for the backoff |
| `onStart()` / `onStop()` | Opens the session and listens to the history / stops listening |
| `getScope<T>(vsid)` / `getBaseUrl()` | The current scope of a view / the WebSocket base URL |

Also exported: `BaseViewClass`, `BasePanelClass`, `ViewBinding`, `BROWSER_VID`, and the types `BrowserScope`, `ViewProps` and `ViewComponent`.

## Protocol

- The page that serves the shell sets the cookies `app_id` (the session id; kept in `sessionStorage`) and `app_skey` (the server's RSA public key).
- The WebSocket opens at `ws(s)://<host>/dispatcher/<app_id>`, subprotocol `wdc`. Its first message is a ping with the last processed request id, the current path and the session signature.
- Each request carries a `requestId`, the events as `"<vsid>:<eventId>"` and the queued form fields under each view's `vsid`. Requests not yet acknowledged are sent again after a reconnection.
- Each response carries the last processed `requestId` and, when present, the new `uri` (written to the hash), the `scopes` (one per view, by id) and the `releasedViews` (views the server let go of, applied after the scopes).
- A scope field ending in `ViewId` references a child view, which is held while referenced. Lists of child views (`<name>ViewIds`) are not held automatically; a view is collected only after it leaves the screen.
- Browser view events: `-1` start (with `p.path`), `-2` history change, `2` keep-alive (every 15 s, and when the page becomes visible or focused). The other events of the browser view (such as dismissing the alert) are the shell's.
- Close code `4001` means the session is no longer valid: the page reloads.

## Security

1. **Handshake.** The bridge generates a random password, derives an AES-256-GCM key from it with PBKDF2 (SHA-256, 250,000 iterations, random salt) and encrypts the password with the server's RSA public key (textbook RSA, no padding). The signature `<rsa password, base 36>.<salt>` goes to the server in the `app_signature` cookie and in the first WebSocket message.
2. **Sensitive data.** Values passed through `cipher()` (the password at login, for instance) travel encrypted with the session key, each with a random 12-byte IV of its own in front of it: `Base64(IV ‖ ciphertext and tag)`. Reusing one IV with the same GCM key would let whoever sees two values learn their XOR and forge tags. The server decrypts them (`RemoteDataSecurity`).
