# remote.react.kotlin

The **remote view's client written in Kotlin**: React + Material UI through kotlin-wrappers, compiled to JavaScript by
the Kotlin toolchain (Kotlin/JS, webpack through the Gradle plugin).

In the remote view the presenters run on the server (`framework-cube-remote-host`, on Javalin). The server sends the
scopes over a WebSocket; this client only paints them and sends back what the user typed (form fields) and did
(events). It knows the protocol — vids, field names, event codes — and nothing else: no presenters, no domain, no
persistence.

It is the Kotlin counterpart of the TypeScript client ([`remote.react.typescript`](../remote.react.typescript/)) and of the
Java version's TeaVM client: same screens, same texts, same protocol, so the same server drives any of them.

It builds on the framework modules [`:framework-cube-remote-bridge`](../../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/remote.bridge.kotlin/) and [`:framework-cube-remote-bridge-react`](../../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/remote.bridge.kotlin.react/)
(`RemoteBridge`, `RemoteScope`, and the React bindings `RemoteViews`, `useView`, `remoteView`), which do the
session, the security handshake, the WebSocket exchange, the history and the view collection.

## How a view looks

```kotlin
val CartItemView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)          // repaints when the server sends this view's scope
    val quantity = scope.int("quantity") ?: 0

    IconButton {
        onClick = {
            bridge.setFormField(props.vsid, "p.quantity", quantity + 1)
            bridge.submit(props.vsid, 2)            // the row's "modify quantity" event
        }
    }
}
```

A nested scope arrives as the id of its view (`"<name>ViewId"`, or `"<name>ViewIds"` for a list) and is drawn with
`remoteView(scope.viewId("content"))`. Each view is registered by its vid in `views/StoreViews.kt`.

| View | vid | Events (form fields) |
|------|-----|----------------------|
| Browser | `7b32e816a191` | 1 alert OK (the bridge sends -1, -2 and 2 itself) |
| Root | `f2d345c4a610` | — |
| Login | `c677cda52d14` | 1 sign in (`userName`, `password` ciphered with the session's key) |
| Home | `473dbdd7a36a` | 1 sign out, 2 open the cart |
| Products panel | `a1b2c3d4e5f6` | 1 open a product (`p.productId`) |
| Purchases panel | `b3c4d5e6f7a8` | 1 open a receipt (`p.purchaseId`), 2 page (`p.page`), 3 rows that fit (`p.capacity`, silent) |
| Product | `48b693f67410` | 1 back, 2 add to cart (`p.quantity`) |
| Cart | `7eb485e5f843` | 1 buy, 3 continue shopping |
| Cart row | `5d9e3c7a1f42` | 1 remove, 2 change the quantity (`p.quantity`) |
| Receipt | `e8d0bd8ae3bc` | 1 continue shopping |

## Build, deploy, run

From `fontes/`:

```bash
./gradlew :view-remote-react-kotlin:jsBrowserDistribution   # production bundle in build/dist/js/productionExecutable
```

To publish it to the backend:

```bash
br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.kotlin/deploy.sh
```

`deploy.sh` builds the production bundle, copies it to `fontes/work/deployment/remote-kotlin/` (not versioned) and
gzips the JavaScript next to it. The backend serves each folder of its working directory's `deployment/` as a context,
so with the backend running (`./gradlew :backend:run`) open <http://localhost:8080/remote-kotlin/>. The page must come
from the backend: it plants the session cookies the bridge needs, and the WebSocket and the product images are at the
backend's root. Deploy again whenever this module or the bridge changes.

## Files

```
remote.react.kotlin/
├── build.gradle.kts          Kotlin/JS executable, kotlin-wrappers (React, MUI) and the npm packages
├── deploy.sh                 builds and publishes to fontes/work/deployment/remote-kotlin/
└── src/jsMain/
    ├── resources/
    │   ├── index.html        the page: fonts, scrollbars, #root
    │   └── favicon.png
    └── kotlin/br/com/wdc/store/view/remote/kotlin/
        ├── Main.kt           creates the bridge, registers the views, renders the browser view, starts
        ├── theme/StoreTheme.kt   palette and MUI theme
        ├── util/Format.kt    prices, dates (MM/DD/YYYY, UTC), product image path, HTML to text
        └── views/
            ├── StoreViews.kt     the vids and their registration; useBridge()
            ├── BrowserView.kt    progress bar, "Not connected" bar, the server's alert, the content
            ├── RootView.kt
            ├── LoginView.kt
            ├── ProductView.kt
            ├── CartView.kt       the cart and its rows (CartItemView)
            ├── ReceiptView.kt
            └── home/
                ├── HomeView.kt           header, then the content or the two panels (tabs when narrow)
                ├── HeaderPanel.kt
                ├── ProductsPanelView.kt
                └── PurchasesPanelView.kt
```
