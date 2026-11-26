# remote-bridge

TypeScript client of the **remote Cube MVP** protocol: the browser side of the remote backend ([`framework-cube-remote-backend`](../remote.backend/)). The presenters run on the server; this package connects to it over WebSocket, does the security handshake, keeps the scopes the server publishes (each presenter's view state, serialized), follows the history, and sends the views' events and form fields back.

It depends on no UI technology: it never draws anything. Painting a scope is the job of a technology binding on top of it — [`wdc-cube-remote-bridge-react`](../remote.bridge.typescript.react/) for React — or of a shell that talks to it directly. It knows nothing about any application either: which views exist and how they look belong to the shell (in this repository, the WDC Store client, [`remote.react.typescript`](../../../br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript/)). The same protocol has a Kotlin/JS client, [`framework-cube-remote-bridge`](../remote.bridge.kotlin/).

The package name is `wdc-cube-remote-bridge`, to be published on npm with 0.5.0 (the same version as the framework, whose remote backend it speaks to; not published yet):

```bash
npm install wdc-cube-remote-bridge history universal-cookie
```

It ships as ES modules with their types (`lib/`, built from `src/` by `npm run build`); `history` and `universal-cookie` are peer dependencies. In this repository, the WDC Store client imports it from source instead, through a bundler alias (below), so a change to the bridge shows at once.

## Structure

```
src/
  index.ts                # Public entry
  ScopeCoordinator.ts     # Session id, handshake, history, view bindings, form fields, events
  FlushRequestContext.ts  # The WebSocket: batches events and form fields, applies scopes and releasedViews,
                          # keep-alive, the "submitting" flag
  ReconnectController.ts  # Reconnection with linear backoff: min(2000·n, 120000) ms
  DataSecurity.ts         # Key agreement (ECDH P-256 + HKDF) and AES-GCM for sensitive fields
  ViewBinding.ts          # Holds one view's scope; calls its forceUpdate when a new scope arrives
  ViewGarbageCollector.ts # Drops views the server released or that left the screen
  types.ts                # BrowserScope, FormMapType
  constants.ts            # Browser view id, keep-alive interval, PROTOCOL_VERSION, OUTDATED_MESSAGE
  utils/
    Base64.ts             # Standard Base64 through btoa/atob; invalid input throws
    UTF8.ts               # TextEncoder/TextDecoder helpers
    LangUtils.ts          # Small helpers (unique id, clearing an object)
```

## Integrating it

Its own `node_modules` are only for building, testing and linting. At runtime its libraries are **peer dependencies** (`history` for the hash history, `universal-cookie` for the session cookies), installed in the shell. Because its files live outside the shell's folder, both the bundler and TypeScript must be told where to find it and where its peer dependencies are.

`package.json` of the shell (Parcel):

```json
"alias": {
  "wdc-cube-remote-bridge": "<path to this folder>/src/index.ts",
  "history": "./node_modules/history",
  "universal-cookie": "./node_modules/universal-cookie"
}
```

`tsconfig.json` of the shell:

```jsonc
"paths": {
  "wdc-cube-remote-bridge": ["<path to this folder>/src/index.ts"],
  "history": ["./node_modules/history"],
  "universal-cookie": ["./node_modules/universal-cookie"]
}
```

A shell that uses a technology binding declares this package's alias too: the binding imports it as `wdc-cube-remote-bridge`.

## Writing a binding for a UI library

A binding (or a shell) creates **one** `ScopeCoordinator` per page and drives it:

```ts
import { ScopeCoordinator, BROWSER_VSID, type BrowserScope } from 'wdc-cube-remote-bridge'

const coordinator = new ScopeCoordinator()

// Drawing a view: its binding holds the scope the server last published.
const binding = coordinator.viewBinding(BROWSER_VSID) // created empty if no scope has arrived yet
binding.forceUpdate = () => repaint() // called on every new scope of this view
coordinator.viewGarbageCollector.mount(BROWSER_VSID) // the view is on screen
draw(binding.getScope() as BrowserScope)

// Opening the session (reads the hash, connects, sends the start event) and closing it.
coordinator.onStart()
coordinator.onStop()

// An event: queue the view's form fields, then submit.
coordinator.setFormField(vsid, 'userName', userName)
coordinator.setFormField(vsid, 'password', await coordinator.dataSecurity.b64Cipher(password))
coordinator.submit(vsid, ON_ENTER)
```

The view's lifecycle on the client:

- A scope field ending in `ViewId` holds the vsid of a child view (`"<viewId>:<instance>"`); a field ending in `ViewIds` holds a list of them. The binding picks how to draw each view id.
- `viewBinding(vsid)` gets or creates a view's binding; `findView(vsid)` gets a live one, taking it back from the collector if it was about to be dropped (`null` if it is gone).
- When a view enters the screen, call `viewGarbageCollector.mount(vsid)`; when it leaves, `viewGarbageCollector.mark(binding)`. A view the server released is dropped when it leaves the screen; any other view that left it is dropped about 4 seconds later, unless it came back or is held.
- `viewGarbageCollector.hold(vsid)` / `release(vsid)` keep a view the binding does not draw from being collected. Children referenced by a `ViewId` field are held automatically while referenced.

## API

| Member of `ScopeCoordinator` | What it does |
|---|---|
| `viewBinding(vsid)` / `findView(vsid)` | A view's binding, created if missing / a live view's binding, or `null` |
| `viewMap` | The bindings of the live views, by vsid |
| `viewGarbageCollector` | `mount`, `mark`, `hold`, `release`, `recover`: see the lifecycle above |
| `setFormField(vsid, name, value)` | Queues a value for the next event |
| `submit(vsid, eventId)` | Sends an event; raises the `submitting` flag of the browser scope if it takes long |
| `submitSilent(vsid, eventId)` | Sends an event without the `submitting` flag |
| `dataSecurity.b64Cipher(text)` | Encrypts a sensitive value with the current socket's key (AES-GCM, Base64); waits for the handshake if it has not completed |
| `connect()` | Reconnects now, without waiting for the backoff |
| `onStart()` / `onStop()` | Opens the session and listens to the history / stops listening |
| `baseWebSocketUrl` | The WebSocket base URL |
| `outdatedMessage` | The text shown in the browser's alert when the host refuses the protocol version; English (`OUTDATED_MESSAGE`) by default, an application may give a translation |

Also exported: `ViewBinding` (`getId()`, `getScope()`, `forceUpdate`), `DataSecurity`, `BROWSER_VID` and `BROWSER_VSID` (the browser view, instance 0, root of every screen), `PROTOCOL_VERSION`, and the types `BrowserScope` and `FormMapType`.

## Protocol

- The page that serves the shell sets one cookie, `app_id`: the session id, `<random>.<signature>` (Base62, signed by the host with HMAC-SHA256), kept in `sessionStorage`.
- The WebSocket opens at `ws(s)://<host>/dispatcher/<app_id>`, subprotocol `wdc`. Its first message, the hello, is a ping with the last processed request id, the current path, the client's public key for the handshake (`clientKey`) and the protocol version (`protocol`, `PROTOCOL_VERSION`); nothing else goes before the host answers it. The answer brings the host's key (`serverKey`) and version. A host that does not speak the version answers `unsupported-protocol`: the client stops (no more requests, no reconnection) and shows, in the browser's alert, that the page is out of date; the alert's OK reloads the page.
- Each request carries a `requestId`, the events as `"<vsid>:<eventId>"` and the queued form fields under each view's `vsid`. Requests not yet acknowledged are sent again after a reconnection.
- Each response carries the last processed `requestId` and, when present, the new `uri` (written to the hash), the `scopes` (one per view, by id) and the `releasedViews` (views the server let go of, applied after the scopes).
- A scope field ending in `ViewId` references a child view, which is held while referenced. Lists of child views (`<name>ViewIds`) are not held automatically; a view is collected only after it leaves the screen.
- Browser view events: `-1` start (with `p.path`), `-2` history change, `2` keep-alive (every 15 s, and when the page becomes visible or focused). The other events of the browser view (such as dismissing the alert) are the shell's.
- The browser scope (`BrowserScope`) carries the content view (`contentViewId`), the alert, the reconnection state (`error`) and the activity flag (`submitting`); the client updates the last two itself.
- Close code `4001` means the session is no longer valid: the page reloads.

The full specification, and the shared fixtures, are in [conformance/](../conformance/README.md).

## Tests

`npm ci && npm test` runs the shared client fixtures of [conformance/client](../conformance/client/) against this client (Vitest, jsdom, a fake WebSocket). The packages it installs are only for building, testing and linting: a shell still gives the bridge its peer dependencies.

Other commands: `npm run build` (compiles `src/` to `lib/`), `npm run lint` (Oxlint), `npm run format:check` (Prettier).

## Security

1. **Handshake.** Every time the WebSocket opens, the client makes an ephemeral ECDH P-256 key pair (Web Crypto, private key not extractable) and sends its public key, Base64 SPKI, as `clientKey` in the hello. The host answers with its own public key as `serverKey`. Both sides derive the AES-256-GCM key from the ECDH shared secret with HKDF-SHA256, salt = the session id (UTF-8), info = `wdc-cube-remote/aes-256-gcm/v1`. Neither side keeps a long-lived key for this, so a recorded session cannot be deciphered later (forward secrecy). A reconnection makes a new pair and a new key; values ciphered with the old one are no longer valid.
2. **Sensitive data.** Values passed through `dataSecurity.b64Cipher()` (the password at login, for instance) travel encrypted with the key of the current socket, each with a random 12-byte IV of its own in front of it: `Base64(IV ‖ ciphertext and tag)`. Reusing one IV with the same GCM key would let whoever sees two values learn their XOR and forge tags. The server decrypts them (`RemoteDataSecurity`). A value ciphered before the handshake completes (a login typed while the socket connects) waits for the key; if no handshake completes in 15 s, `b64Cipher` rejects.
3. **Cookies and signatures.** The only cookie is `app_id`. The host signs the session id and the addresses it writes to the history with HMAC-SHA256; the client only carries the signatures.
