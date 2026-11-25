package br.com.wdc.store.view.remote.react.kotlin

import br.com.wdc.framework.cube.remote.bridge.RemoteBridge
import br.com.wdc.framework.cube.remote.bridge.react.RemoteViews
import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewsContext
import br.com.wdc.store.view.remote.react.kotlin.theme.StoreTheme
import br.com.wdc.store.view.remote.react.kotlin.views.registerStoreViews
import mui.material.CssBaseline
import mui.material.styles.ThemeProvider
import react.FC
import react.Props
import react.create
import react.dom.client.createRoot
import web.dom.ElementId
import web.dom.document

/**
 * The remote view's client: the presenters run on the server, which sends the scopes over a WebSocket; this page paints
 * them, starting from the browser view, and sends back what the user typed and did.
 */
fun main() {
    val bridge = RemoteBridge()
    val views = RemoteViews(bridge).registerStoreViews()

    val container = document.getElementById(ElementId("root")) ?: return
    val root = FC<Props> {
        ThemeProvider {
            theme = StoreTheme
            CssBaseline()
            RemoteViewsContext.Provider(views) {
                views.render(this, RemoteBridge.BROWSER_VSID)
            }
        }
    }
    createRoot(container).render(root.create())

    bridge.start()
}
