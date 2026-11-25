# framework-cube-remote-bridge-react

React bindings, with [kotlin-wrappers](https://github.com/JetBrains/kotlin-wrappers), for the Kotlin/JS client of the remote Cube host ([`framework-cube-remote-bridge`](../remote.bridge.kotlin/)): which component paints each vid, and the hooks a view uses. The WDC Store's [Kotlin remote client](../../../br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.kotlin/) is built on it.

Kotlin/JS (browser) only. Gradle: `implementation("io.github.mrcdom.wdc.kt:framework-cube-remote-bridge-react:<version>")` (from 0.5.0). It brings the bridge and kotlin-wrappers' `react`; the shell adds the `react` npm package.

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
    // a nested scope's view: remoteView(scope.viewId("content"))
}
```

| Piece | What it does |
|---|---|
| `RemoteViews` | The vid → component catalog (each component memoized: a parent repaint does not repaint it) and `render(builder, vsid)`. |
| `RemoteViewsContext` | The catalog the views draw with, provided around the root. |
| `remoteView(vsid)` | Draws the view of a nested scope in its parent's. |
| `useView(vsid)` | Ties the component to the view: it repaints each time the host sends the scope, and the collector knows when it is on screen. |
| `useHold(vsid)` | Keeps a view while the component is on screen, even if nothing shows it yet. |
