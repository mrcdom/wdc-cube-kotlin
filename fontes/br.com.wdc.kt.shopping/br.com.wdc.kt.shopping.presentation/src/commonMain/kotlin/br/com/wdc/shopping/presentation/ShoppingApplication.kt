package br.com.wdc.shopping.presentation

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.open.login.LoginKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.presenter.RootPresenter
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

    fun getRootPlace(): CubeTreePlace = RootKeys.PLACE

    open fun getRootPresenter(): RootPresenter? =
        getPresenter(RootKeys.PLACE.id) as? RootPresenter

    fun getSecurityContext(): SecurityContext? = securityContext

    fun setSecurityContext(ctx: SecurityContext?) {
        this.securityContext = ctx
    }

    // The repositories are the ones registered by the composition root: on the server, the ones that talk to the
    // database; on a client, the ones that talk to the REST API — which is where access is checked.

    fun getUserRepository(): UserRepository = UserRepository.BEAN.get()

    fun getProductRepository(): ProductRepository = ProductRepository.BEAN.get()

    fun getPurchaseRepository(): PurchaseRepository = PurchaseRepository.BEAN.get()

    fun getPurchaseItemRepository(): PurchaseItemRepository = PurchaseItemRepository.BEAN.get()

    // :: API

    open fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        getRootPresenter()?.alertUnexpectedError(logger, message, e)
    }

    /** Navigates to an address (`product?pr-id=7`): an unknown place goes to the root. */
    suspend fun flip(address: String): Boolean = flip(CubeIntent.parse(address))

    /** Navigates to the place [intent] names (an unknown one goes to the root), with its parameters. */
    suspend fun flip(intent: CubeIntent): Boolean = flip(placeOf(intent.place?.placeName), intent)

    companion object {
        /**
         * Every place an address can name, listed by hand: each keys class declares its own place, and a place is
         * created when its keys are first used — this list creates them all before the first navigation.
         */
        val PLACES: List<CubeTreePlace> = listOf(
            RootKeys.PLACE,
            LoginKeys.PLACE,
            HomeKeys.PLACE,
            CartKeys.PLACE,
            ProductKeys.PLACE,
            ReceiptKeys.PLACE,
        )

        private val placesByName: Map<String, CubeTreePlace> = PLACES.associateBy { it.placeName }

        /** The place with [placeName], or the root when no place has it. */
        fun placeOf(placeName: String?): CubeTreePlace = placesByName[placeName] ?: RootKeys.PLACE
    }

    protected abstract fun createSessionStorage(): SessionStorage
}
