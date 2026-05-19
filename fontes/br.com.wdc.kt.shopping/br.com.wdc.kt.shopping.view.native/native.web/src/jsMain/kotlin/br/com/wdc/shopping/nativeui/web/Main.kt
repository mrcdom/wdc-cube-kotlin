package br.com.wdc.shopping.nativeui.web

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.bridge.RenderSlot
import br.com.wdc.shopping.nativeui.web.bridge.WorkerProxy
import br.com.wdc.shopping.nativeui.web.theme.ShoppingTheme
import br.com.wdc.shopping.nativeui.web.views.*
import kotlinx.browser.window
import mui.material.CssBaseline
import mui.material.styles.ThemeProvider
import react.FC
import react.Props
import react.create
import react.dom.client.createRoot
import react.useEffect
import react.useState
import web.dom.ElementId
import web.dom.document

/** The singleton WorkerProxy that manages the Worker bridge. */
private lateinit var proxy: WorkerProxy

/** The root view instance — set when Worker sends viewCreated for the root. */
private var rootView: ReactCubeView? = null

/**
 * View factory: creates the appropriate ReactCubeView for a given viewType.
 */
private fun createView(viewId: String, viewType: String): ReactCubeView {
    val view = when (viewType) {
        "RootView" -> RootView(viewId, proxy)
        "LoginView" -> LoginView(viewId, proxy)
        "HomeView" -> HomeView(viewId, proxy)
        "CartView" -> CartView(viewId, proxy)
        "ProductView" -> ProductView(viewId, proxy)
        "ReceiptView" -> ReceiptView(viewId, proxy)
        "ProductsPanelView" -> ProductsPanelView(viewId, proxy)
        "PurchasesPanelView" -> PurchasesPanelView(viewId, proxy)
        else -> error("Unknown viewType: $viewType")
    }
    proxy.registerView(viewId, view)
    return view
}

private fun getLocationHash(): String {
    val h = window.location.hash
    return if (h.startsWith("#")) h.substring(1) else h
}

fun main() {
    // Install serialization (needed for JsonInputFactory used by views' readState)
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()

    // Inject subtle scrollbar CSS
    document.head?.let { head ->
        val style = document.createElement("style")
        style.textContent = """
            ::-webkit-scrollbar { width: 6px; }
            ::-webkit-scrollbar-track { background: transparent; }
            ::-webkit-scrollbar-thumb { background-color: white; border-radius: 3px; transition: background-color 0.2s; }
            ::-webkit-scrollbar-thumb:hover { background-color: rgba(0,0,0,0.25); }
            ::-webkit-scrollbar-thumb:active { background-color: rgba(0,0,0,0.4); }
            * { scrollbar-width: thin; scrollbar-color: white transparent; }
            *:hover { scrollbar-color: rgba(0,0,0,0.15) transparent; }
        """.trimIndent()
        head.appendChild(style)
    }

    proxy = WorkerProxy()

    // When the Worker creates a view, instantiate its main-thread counterpart
    proxy.onViewCreated = { viewId, viewType ->
        val view = createView(viewId, viewType)
        // The first RootView created becomes the root of the React tree
        if (viewType == "RootView" && rootView == null) {
            rootView = view
        }
    }

    // When the Worker releases a view, clean up
    proxy.onViewReleased = { viewId ->
        // proxy.unregisterView is already called inside WorkerProxy
    }

    // When the Worker updates history, sync the browser URL
    proxy.onHistoryUpdate = { hash ->
        if (hash != getLocationHash()) {
            window.location.hash = "#$hash"
        }
    }

    // Listen for browser back/forward and manual URL hash changes
    window.addEventListener("hashchange", {
        val newHash = getLocationHash()
        proxy.hashChange(newHash)
    })

    // Start the Worker
    val baseUrl = window.location.origin
    val initialHash = getLocationHash()
    proxy.start("view-native-web-worker.js", baseUrl, initialHash)

    // Mount React
    val container = document.getElementById(ElementId("app")) ?: return

    val rootComponent = FC<Props> {
        ThemeProvider {
            theme = ShoppingTheme

            CssBaseline()

            var rev by useState(0)
            useEffect(Unit) {
                proxy.onReady = { rev++ }
            }

            @Suppress("UNUSED_VARIABLE")
            val unused = rev

            val root = rootView
            if (root != null) {
                RenderSlot {
                    view = root
                }
            }
        }
    }

    createRoot(container).render(rootComponent.create())
}
