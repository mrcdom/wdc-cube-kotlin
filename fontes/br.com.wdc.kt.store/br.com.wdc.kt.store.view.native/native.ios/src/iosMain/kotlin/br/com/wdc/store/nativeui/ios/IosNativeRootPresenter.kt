package br.com.wdc.store.nativeui.ios

import br.com.wdc.framework.commons.storage.IosPersistentSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.store.nativeui.ios.toolkit.ViewUpdateScheduler
import br.com.wdc.store.nativeui.ios.views.*
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
import kotlin.reflect.KClass

/**
 * The iOS application, and the UIKit rendering technology's binding between scopes and views: which view draws each
 * kind of scope, and the view of each scope drawn, until it is released. Views are UIKit objects: everything here
 * runs on the main thread.
 */
class IosNativeRootPresenter : RootPresenter() {

    override fun createSessionStorage(): SessionStorage = IosPersistentSessionStorage()

    /** Which view draws each scope class: the presenters', the panels' and the rows'. */
    private val catalog: Map<KClass<out Scope>, (Scope) -> AbstractViewIos<*>> = mapOf(
        RootScope::class to { RootViewIos(this, it as RootScope) },
        LoginScope::class to { LoginViewIos(this, it as LoginScope) },
        HomeScope::class to { HomeViewIos(this, it as HomeScope) },
        ProductsPanelScope::class to { ProductsPanelViewIos(this, it as ProductsPanelScope) },
        PurchasesPanelScope::class to { PurchasesPanelViewIos(this, it as PurchasesPanelScope) },
        ProductScope::class to { ProductViewIos(this, it as ProductScope) },
        CartScope::class to { CartViewIos(this, it as CartScope) },
        CartItemScope::class to { CartItemViewIos(this, it as CartItemScope) },
        ReceiptScope::class to { ReceiptViewIos(this, it as ReceiptScope) },
    )

    /** The view of each scope drawn (scopes compare by identity). */
    private val views = mutableMapOf<Scope, AbstractViewIos<*>>()

    /**
     * The view that draws [scope], made the first time it is asked for: the scope tells it of each change from then
     * on, and it draws once right away.
     */
    fun viewOf(scope: Scope): AbstractViewIos<*> {
        views[scope]?.let { return it }
        val factory = catalog[scope::class] ?: throw IllegalStateException("No view registered for ${scope::class.simpleName}")
        val view = factory(scope)
        views[scope] = view
        scope.setUpdater(view.updater)
        view.initialize()
        return view
    }

    /** The view was released: it no longer draws its scope, and leaves the dirty ones. */
    fun forgetView(view: AbstractViewIos<*>) {
        if (views[view.scope] === view) views.remove(view.scope)
        ViewUpdateScheduler.removeDirty(view)
    }

    /** A snapshot of the views alive, for what applies to all of them. */
    fun liveViews(): List<AbstractViewIos<*>> = views.values.toList()
}
