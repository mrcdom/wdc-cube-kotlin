# framework-cube-remote-bridge

The client side of the remote Cube host ([`framework-cube-remote`](../br.com.wdc.kt.framework.cube.remote/README.md)), in Kotlin, compiled to JavaScript by the Kotlin toolchain: the presenters run on the server, and a browser page written in Kotlin paints the scopes they show. It speaks the same protocol as the TypeScript bridge ([`@wdc/remote-bridge-react`](../br.com.wdc.kt.framework.cube.remote.react/)), and comes with bindings for React shells written with [kotlin-wrappers](https://github.com/JetBrains/kotlin-wrappers). The WDC Store's [Kotlin remote client](../../br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.react/remote.react.kotlin/) is built on it.

Kotlin/JS (browser) only. Gradle: `implementation("io.github.mrcdom.wdc.kt:framework-cube-remote-bridge:<version>")` (from 0.5.0), in a `js { browser() }` source set. It brings `framework-commons` and kotlin-wrappers' `react`; the shell adds the `react` npm package.

## How a shell uses it

```kotlin
val bridge = RemoteBridge()
val views = RemoteViews(bridge)
    .register(RemoteBridge.BROWSER_VID, BrowserView)
    .register("c677cda52d14", LoginView) // the vid each scope declares on the server

fun main() {
    createRoot(document.getElementById("root")!!).render(
        FC {
            RemoteViewsContext.Provider(views) { views.render(this, RemoteBridge.BROWSER_VSID) }
        }.create()
    )
    bridge.start()
}

val LoginView = FC<RemoteViewProps> { props ->
    val bridge = useRequired(RemoteViewsContext).bridge
    val scope = bridge.useView(props.vsid)       // repaints when the host sends the scope
    // … scope.string("errorMessage"), scope.boolean("loading")
    // on submit:
    //   bridge.setFormField(props.vsid, "userName", userName)
    //   bridge.setFormField(props.vsid, "password", bridge.cipher(password))
    //   bridge.submit(props.vsid, 1)
}
```

A nested scope is drawn in its parent with `remoteView(scope.viewId("content"))`.

## Main classes

| Class | Responsibility |
|-------|----------------|
| `RemoteBridge` | One per page. Holds the session id (the `app_id` cookie, kept in `sessionStorage` for reloads) and runs the handshake (the `app_skey` cookie). It also holds the history (the page's hash, signed by the host) and each view's binding. Form data goes in with `setFormField`, events with `submit`/`submitSilent`, and `cipher` ciphers a sensitive value. |
| `RemoteScope` | The values of a scope, read by name: `string`, `int`, `long`, `double`, `boolean`, `obj`, `objects`, `strings`; `viewId(name)`/`viewIds(name)` for the nested scopes (`"<name>ViewId"`, `"<name>ViewIds"`). |
| `ViewBinding` | One published view: its values, the views it refers to, and who repaints. |
| `DataSecurity` | The handshake, `<password ciphered with RSA, base 36>.<salt>`, and the session's cipher. The cipher is AES-256-GCM, with a key derived by PBKDF2 (250,000 iterations) through the browser's Web Crypto, and a random IV for every value: `Base64(IV ‖ ciphertext and tag)`. The RSA comes from `framework-commons`. |
| `RequestChannel` (internal) | The WebSocket. Queued requests are merged into one message, and each answer clears what it covers and brings the scopes and `releasedViews`. A keep-alive goes out every 15 s, and the browser's `submitting` flag is set while a user's request waits. |
| `ReconnectController` (internal) | Reconnects after a drop, waiting `min(2000·n, 120000)` ms, and shows it in the browser's `error`. |
| `ViewGarbageCollector` (internal) | A view off screen is dropped 4 s later unless held: by a scope that refers to it, or by `useHold`. A view the host released goes at once. |
| `react.RemoteViews`, `RemoteViewsContext`, `remoteView`, `useView`, `useHold` | The React side: the vid → component catalog (memoized components), the slot of a nested view, and the hooks. |

## Tests

`ViewBindingTest` (reading a scope, the views it refers to) and `DataSecurityTest` (the handshake's shape, a new IV per value, a changed byte refused), run in a headless Chrome (`./gradlew :framework-cube-remote-bridge:jsTest`).
