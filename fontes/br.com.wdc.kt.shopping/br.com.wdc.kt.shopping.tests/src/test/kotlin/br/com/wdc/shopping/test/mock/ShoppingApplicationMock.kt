package br.com.wdc.shopping.test.mock

import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.presentation.SecuredProductRepository
import br.com.wdc.shopping.presentation.SecuredPurchaseItemRepository
import br.com.wdc.shopping.presentation.SecuredPurchaseRepository
import br.com.wdc.shopping.presentation.SecuredUserRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.test.mock.viewimpl.CartViewMock
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductsPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.PurchasesPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ReceiptViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RootViewMock
import java.util.concurrent.ConcurrentHashMap

class ShoppingApplicationMock : ShoppingApplication() {

    init {
        RootPresenter.createView = { p -> RootViewMock(p.app as ShoppingApplicationMock, p) }
        LoginPresenter.createView = { p -> LoginViewMock(p.app as ShoppingApplicationMock, p) }
        HomePresenter.createView = { p -> RestrictedViewMock(p.app as ShoppingApplicationMock, p) }
        CartPresenter.createView = { p -> CartViewMock(p.app as ShoppingApplicationMock, p) }
        ProductPresenter.createView = { p -> ProductViewMock(p.app as ShoppingApplicationMock, p) }
        ReceiptPresenter.createView = { p -> ReceiptViewMock(p.app as ShoppingApplicationMock, p) }
        ProductsPanelPresenter.createView = { p -> ProductsPanelViewMock(p) }
        PurchasesPanelPresenter.createView = { p -> PurchasesPanelViewMock(p) }
    }

    private val attributes = HashMap<String, Any?>()

    override fun createPresenterMap(): MutableMap<Int, CubePresenter> = ConcurrentHashMap()

    override fun createUserDelegate(delegate: UserRepository) =
        SecuredUserRepository(delegate, ::getSecurityContext)

    override fun createProductDelegate(delegate: ProductRepository) =
        SecuredProductRepository(delegate, ::getSecurityContext)

    override fun createPurchaseDelegate(delegate: PurchaseRepository) =
        SecuredPurchaseRepository(delegate, ::getSecurityContext)

    override fun createPurchaseItemDelegate(delegate: PurchaseItemRepository) =
        SecuredPurchaseItemRepository(delegate, ::getSecurityContext)

    fun getRootView(): RootViewMock? {
        val rootPresenter = getRootPresenter()
        val v = rootPresenter?.view()
        return if (v is RootViewMock) v else null
    }

    override fun setAttribute(name: String, value: Any?): Any? {
        return attributes.put(name, value)
    }

    override fun getAttribute(name: String): Any? {
        return attributes[name]
    }

    override fun removeAttribute(name: String): Any? {
        return attributes.remove(name)
    }

    override fun updateHistory() {
        // NOOP
    }
}
