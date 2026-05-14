package br.com.wdc.shopping.view.compose.web

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.concurrent.WasmScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.WasmCryptoProvider
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestRepositoryBootstrap
import br.com.wdc.shopping.persistence.client.WasmHttpTransport
import br.com.wdc.shopping.presentation.repository.SecuredProductRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseItemRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseRepository
import br.com.wdc.shopping.presentation.repository.SecuredUserRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.view.compose.bridge.ComposeCubeView
import br.com.wdc.shopping.view.compose.views.*
import br.com.wdc.shopping.view.compose.theme.ShoppingTheme
import kotlinx.browser.document

private class ComposeShoppingApplication : ShoppingApplication() {

    private val attributes = mutableMapOf<String, Any?>()

    override fun setAttribute(name: String, value: Any?): Any? = attributes.put(name, value)

    override fun getAttribute(name: String): Any? = attributes[name]

    override fun removeAttribute(name: String): Any? = attributes.remove(name)

    override fun updateHistory() {
        // Could integrate with browser history API
    }

    override fun createPresenterMap(): MutableMap<Int, CubePresenter> = LinkedHashMap()

    override fun createUserDelegate(delegate: UserRepository) =
        SecuredUserRepository(delegate) { getSecurityContext() }

    override fun createProductDelegate(delegate: ProductRepository) =
        SecuredProductRepository(delegate) { getSecurityContext() }

    override fun createPurchaseDelegate(delegate: PurchaseRepository) =
        SecuredPurchaseRepository(delegate) { getSecurityContext() }

    override fun createPurchaseItemDelegate(delegate: PurchaseItemRepository) =
        SecuredPurchaseItemRepository(delegate) { getSecurityContext() }
}

private fun createView(view: ComposeCubeView): CubeView {
    return view
}

private fun initializePlatform(baseUrl: String) {
    // Install platform services
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(WasmScheduledExecutor())

    // Initialize REST repositories
    val transport = WasmHttpTransport(baseUrl)
    val config = RestConfig(transport)
    RestRepositoryBootstrap.initialize(config, WasmCryptoProvider())

    // Wire view factories
    RootPresenter.createView = { p -> createView(RootView(p)) }
    LoginPresenter.createView = { p -> createView(LoginView(p)) }
    HomePresenter.createView = { p -> createView(HomeView(p)) }
    CartPresenter.createView = { p -> createView(CartView(p)) }
    ProductPresenter.createView = { p -> createView(ProductView(p)) }
    ReceiptPresenter.createView = { p -> createView(ReceiptView(p)) }
    ProductsPanelPresenter.createView = { p -> createView(ProductsPanelView(p)) }
    PurchasesPanelPresenter.createView = { p -> createView(PurchasesPanelView(p)) }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val baseUrl = getBaseUrl()
    initializePlatform(baseUrl)

    val app = ComposeShoppingApplication()
    app.go("public") // Navigate to root/login

    val target = document.getElementById("ComposeTarget") ?: return

    ComposeViewport(target) {
        ShoppingTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val rootPresenter = app.getRootPresenter()
                if (rootPresenter != null) {
                    val rootView = rootPresenter.view() as? RootView
                    rootView?.Render()
                }
            }
        }
    }
}

@JsFun("() => window.location.origin")
private external fun jsLocationOrigin(): JsString

private fun getBaseUrl(): String {
    return jsLocationOrigin().toString()
}
