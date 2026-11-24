package br.com.wdc.store.test.mock

import br.com.wdc.framework.commons.storage.JvmSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootScope
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.store.test.mock.viewimpl.AbstractViewMock
import br.com.wdc.store.test.mock.viewimpl.CartItemViewMock
import br.com.wdc.store.test.mock.viewimpl.CartViewMock
import br.com.wdc.store.test.mock.viewimpl.LoginViewMock
import br.com.wdc.store.test.mock.viewimpl.ProductViewMock
import br.com.wdc.store.test.mock.viewimpl.ProductsPanelViewMock
import br.com.wdc.store.test.mock.viewimpl.PurchasesPanelViewMock
import br.com.wdc.store.test.mock.viewimpl.ReceiptViewMock
import br.com.wdc.store.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.store.test.mock.viewimpl.RootViewMock
import java.util.Collections
import java.util.WeakHashMap
import kotlin.reflect.KClass

/** [storage] is the session: a page reload is a new application on the same session. */
class RootPresenterMock(private val storage: SessionStorage = JvmSessionStorage()) : RootPresenter() {

    /** The test views — a rendering technology of their own: which view draws each scope class. */
    private val catalog: Map<KClass<out Scope>, (Scope) -> AbstractViewMock<*>> = mapOf(
        RootScope::class to { RootViewMock(this, it as RootScope) },
        LoginScope::class to { LoginViewMock(this, it as LoginScope) },
        HomeScope::class to { RestrictedViewMock(this, it as HomeScope) },
        CartScope::class to { CartViewMock(this, it as CartScope) },
        CartItemScope::class to { CartItemViewMock(this, it as CartItemScope) },
        ProductScope::class to { ProductViewMock(this, it as ProductScope) },
        ReceiptScope::class to { ReceiptViewMock(this, it as ReceiptScope) },
        ProductsPanelScope::class to { ProductsPanelViewMock(this, it as ProductsPanelScope) },
        PurchasesPanelScope::class to { PurchasesPanelViewMock(this, it as PurchasesPanelScope) },
    )

    /** The views made so far; weak on the scope, so a scope that went away takes its view with it. */
    private val views: MutableMap<Scope, AbstractViewMock<*>> = Collections.synchronizedMap(WeakHashMap())

    /**
     * The view that draws [scope], made the first time a test asks for it: from then on, a change in the scope counts
     * as a repaint of it ([AbstractViewMock.updates]).
     */
    fun viewOf(scope: Scope): AbstractViewMock<*> = views.getOrPut(scope) {
        val factory = catalog[scope::class]
            ?: throw IllegalStateException("No view mock for ${scope::class.simpleName}")
        factory(scope).also { scope.setUpdater(it.updater) }
    }

    override fun createSessionStorage(): SessionStorage = storage

    /** The tests start from the root scope; every other view is asked for from the scope that holds it. */
    fun getRootView(): RootViewMock? = viewOf(scope) as? RootViewMock

    override fun updateHistory() {
        // NOOP
    }
}
