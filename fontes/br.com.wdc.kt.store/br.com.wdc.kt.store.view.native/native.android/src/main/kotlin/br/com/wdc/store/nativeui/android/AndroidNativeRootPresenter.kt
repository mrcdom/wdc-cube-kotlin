package br.com.wdc.store.nativeui.android

import br.com.wdc.framework.commons.storage.AndroidPersistentSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.i18n.Messages
import br.com.wdc.store.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.store.nativeui.android.toolkit.ViewUpdateScheduler
import br.com.wdc.store.nativeui.android.views.*
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
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.reflect.KClass

/**
 * The Android application, and the Android views' binding between scopes and views: which view draws each kind of
 * scope, and the view of each scope drawn, until it is released. Android views live on the main thread, and so does
 * everything here.
 */
class AndroidNativeRootPresenter(
    private val persistentStorage: AndroidPersistentSessionStorage
) : RootPresenter() {

    override fun updateHistory() { /* No browser history on Android */ }

    override fun createSessionStorage(): SessionStorage = persistentStorage

    /** The UI texts in the device's language (pt-BR or, for any other, en-US), read once when the app starts. */
    val messages: Messages = Messages.of(Locale.getDefault().toLanguageTag())

    /** Which view draws each scope class: the presenters', the panels' and the rows'. */
    private val catalog: Map<KClass<out Scope>, (Scope) -> AbstractViewAndroid<*>> = mapOf(
        RootScope::class to { RootViewAndroid(this, it as RootScope) },
        LoginScope::class to { LoginViewAndroid(this, it as LoginScope) },
        HomeScope::class to { HomeViewAndroid(this, it as HomeScope) },
        ProductsPanelScope::class to { ProductsPanelViewAndroid(this, it as ProductsPanelScope) },
        PurchasesPanelScope::class to { PurchasesPanelViewAndroid(this, it as PurchasesPanelScope) },
        ProductScope::class to { ProductViewAndroid(this, it as ProductScope) },
        CartScope::class to { CartViewAndroid(this, it as CartScope) },
        CartItemScope::class to { CartItemViewAndroid(this, it as CartItemScope) },
        ReceiptScope::class to { ReceiptViewAndroid(this, it as ReceiptScope) },
    )

    /** The view of each scope drawn. */
    private val views = IdentityHashMap<Scope, AbstractViewAndroid<*>>()

    /**
     * The view that draws [scope], made the first time it is asked for: the scope tells it of each change from then
     * on, and it draws once right away.
     */
    fun viewOf(scope: Scope): AbstractViewAndroid<*> {
        views[scope]?.let { return it }
        val factory = catalog[scope::class] ?: throw IllegalStateException("No view registered for ${scope::class.simpleName}")
        val view = factory(scope)
        views[scope] = view
        scope.setUpdater(view.updater)
        view.initialize()
        return view
    }

    /** The view was released: it no longer draws its scope, and leaves the dirty ones. */
    fun forgetView(view: AbstractViewAndroid<*>) {
        views.remove(view.scope, view)
        ViewUpdateScheduler.removeDirty(view)
    }

    /** A snapshot of the views alive, for what applies to all of them. */
    fun liveViews(): List<AbstractViewAndroid<*>> = views.values.toList()
}
