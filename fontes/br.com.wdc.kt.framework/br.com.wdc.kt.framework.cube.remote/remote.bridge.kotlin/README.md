# framework-cube-remote-bridge

The client of the remote Cube host ([`framework-cube-remote-host`](../remote.host/)), in Kotlin, compiled to JavaScript by the Kotlin toolchain: the presenters run on the server, and a browser page written in Kotlin paints the scopes they show. It speaks the same protocol as the [TypeScript bridge](../remote.bridge.typescript/) and uses no UI technology: the React bindings are [`framework-cube-remote-bridge-react`](../remote.bridge.kotlin.react/), and any other UI library (Compose HTML, plain DOM) can bind the same way.

Kotlin/JS (browser) only. Gradle: `implementation("io.github.mrcdom.wdc.kt:framework-cube-remote-bridge:<version>")` (from 0.5.0), in a `js { browser() }` source set. It brings `framework-commons`.

## How a client uses it

```kotlin
val bridge = RemoteBridge()   // one per page: reads the session cookies the host planted

// a view of any UI library: draw the scope, repaint when it changes, mark it on and off screen
val binding = bridge.bindingOf(vsid)
binding.onChange = { repaint(binding.scope) }
bridge.mounted(vsid)          // … and bridge.unmounted(binding) when it leaves the screen

// what the user does
bridge.setFormField(vsid, "userName", userName)
bridge.setFormField(vsid, "password", bridge.cipher(password))   // suspend
bridge.submit(vsid, 1)

bridge.start()                // the handshake, then the address in the page's hash
```

## Main classes

| Class | Responsibility |
|-------|----------------|
| `RemoteBridge` | One per page. Holds the session id (the `app_id` cookie, kept in `sessionStorage` for reloads) and runs the handshake (the `app_skey` cookie). It also holds the history (the page's hash, signed by the host) and each view's binding (`bindingOf`, `scopeOf`, `mounted`/`unmounted`, `hold`/`release`). Form data goes in with `setFormField`, events with `submit`/`submitSilent`, and `cipher` ciphers a sensitive value. |
| `RemoteScope` | The values of a scope, read by name: `string`, `int`, `long`, `double`, `boolean`, `obj`, `objects`, `strings`; `viewId(name)`/`viewIds(name)` for the nested scopes (`"<name>ViewId"`, `"<name>ViewIds"`). |
| `ViewBinding` | One published view: its values (`scope`), the views it refers to, and who repaints (`onChange`). |
| `DataSecurity` | The handshake, `<password ciphered with RSA, base 36>.<salt>`, and the session's cipher. The cipher is AES-256-GCM, with a key derived by PBKDF2 (250,000 iterations) through the browser's Web Crypto, and a random IV for every value: `Base64(IV ‖ ciphertext and tag)`. The RSA comes from `framework-commons`. |
| `RequestChannel` (internal) | The WebSocket. Queued requests are merged into one message, and each answer clears what it covers and brings the scopes and `releasedViews`. A keep-alive goes out every 15 s, and the browser's `submitting` flag is set while a user's request waits. |
| `ReconnectController` (internal) | Reconnects after a drop, waiting `min(2000·n, 120000)` ms, and shows it in the browser's `error`. |
| `ViewGarbageCollector` (internal) | A view off screen is dropped 4 s later unless held: by a scope that refers to it, or by `hold`. A view the host released goes at once. |

## Tests

`ViewBindingTest` (reading a scope, the views it refers to) and `DataSecurityTest` (the handshake's shape, a new IV per value, a changed byte refused), run in a headless Chrome (`./gradlew :framework-cube-remote-bridge:jsTest`).
