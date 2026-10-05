package br.com.wdc.shopping.presentation

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePlace
import br.com.wdc.framework.commons.storage.SessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.function.GoAction
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager

abstract class ShoppingApplication : CubeApplication() {

    /**
     * Single-threaded coroutine scope for presenter actions.
     * limitedParallelism(1) guarantees serial execution per application instance,
     * while allowing different application instances to run in parallel.
     */
    val presenterScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    val sessionStorage: SessionStorage by lazy { createSessionStorage() }

    var subject: Subject? = null

    var cart: CartManager? = null

    private var securityContext: SecurityContext? = null

    // :: Getters and Setters

    fun getRootPlace(): CubePlace = Routes.Place.ROOT

    open fun getRootPresenter(): RootPresenter? =
        getPresenter(Routes.Place.ROOT.id) as? RootPresenter

    fun getSecurityContext(): SecurityContext? = securityContext

    fun setSecurityContext(ctx: SecurityContext?) {
        this.securityContext = ctx
    }

    // Os repositórios são os registrados pelo composition root: no servidor, os que falam com o banco; num
    // cliente, os que falam com a API REST — que é onde o acesso é conferido.

    fun getUserRepository(): UserRepository = UserRepository.BEAN.get()

    fun getProductRepository(): ProductRepository = ProductRepository.BEAN.get()

    fun getPurchaseRepository(): PurchaseRepository = PurchaseRepository.BEAN.get()

    fun getPurchaseItemRepository(): PurchaseItemRepository = PurchaseItemRepository.BEAN.get()

    // :: API

    open fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        getRootPresenter()?.alertUnexpectedError(logger, message, e)
    }

    suspend fun go(placeStr: String) {
        go(CubeIntent.parse(placeStr))
    }

    suspend fun go(intent: CubeIntent) {
        Internals.go(this, intent)
    }

    // :: Internal Classes - Meant to be used on initialization only

    object Internals {

        private val goActionMap: MutableMap<String, GoAction> = HashMap()

        fun registerPlace(tag: String, goAction: GoAction) {
            goActionMap[tag] = goAction
        }

        internal suspend fun go(app: ShoppingApplication, place: CubeIntent) {
            val goAction = goActionMap[place.place?.placeName]
                ?: goActionMap[app.getRootPlace().placeName]

            goAction?.apply(app, place)
        }
    }

    protected abstract fun createSessionStorage(): SessionStorage
}
