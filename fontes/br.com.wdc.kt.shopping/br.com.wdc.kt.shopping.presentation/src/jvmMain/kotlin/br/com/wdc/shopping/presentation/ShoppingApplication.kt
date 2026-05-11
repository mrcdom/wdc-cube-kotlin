package br.com.wdc.shopping.presentation

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.parse
import br.com.wdc.framework.cube.CubePlace
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.function.GoAction
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import java.util.concurrent.ConcurrentHashMap

abstract class ShoppingApplication : CubeApplication() {

    var subject: Subject? = null

    var cart: CartManager? = null

    private var securityContext: SecurityContext? = null

    private var userRepository: UserRepository? = null
    private var productRepository: ProductRepository? = null
    private var purchaseRepository: PurchaseRepository? = null
    private var purchaseItemRepository: PurchaseItemRepository? = null

    // :: Getters and Setters

    fun getRootPlace(): CubePlace = Routes.Place.ROOT

    open fun getRootPresenter(): RootPresenter? =
        getPresenter(Routes.Place.ROOT.id) as? RootPresenter

    fun getSecurityContext(): SecurityContext? = securityContext

    fun setSecurityContext(ctx: SecurityContext?) {
        this.securityContext = ctx
        // Recriar delegates quando o contexto muda
        userRepository = null
        productRepository = null
        purchaseRepository = null
        purchaseItemRepository = null
    }

    fun getUserRepository(): UserRepository {
        var repo = userRepository
        if (repo == null) {
            repo = createUserDelegate(UserRepository.BEAN.get())
            userRepository = repo
        }
        return repo
    }

    fun getProductRepository(): ProductRepository {
        var repo = productRepository
        if (repo == null) {
            repo = createProductDelegate(ProductRepository.BEAN.get())
            productRepository = repo
        }
        return repo
    }

    fun getPurchaseRepository(): PurchaseRepository {
        var repo = purchaseRepository
        if (repo == null) {
            repo = createPurchaseDelegate(PurchaseRepository.BEAN.get())
            purchaseRepository = repo
        }
        return repo
    }

    fun getPurchaseItemRepository(): PurchaseItemRepository {
        var repo = purchaseItemRepository
        if (repo == null) {
            repo = createPurchaseItemDelegate(PurchaseItemRepository.BEAN.get())
            purchaseItemRepository = repo
        }
        return repo
    }

    // :: API

    open fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        getRootPresenter()?.alertUnexpectedError(logger, message, e)
    }

    fun go(placeStr: String) {
        go(CubeIntent.parse(placeStr))
    }

    fun go(intent: CubeIntent) {
        Internals.go(this, intent)
    }

    // :: Internal Classes - Meant to be used on initialization only

    object Internals {

        private val goActionMap: MutableMap<String, GoAction> = ConcurrentHashMap()

        fun registerPlace(tag: String, goAction: GoAction) {
            goActionMap[tag] = goAction
        }

        internal fun go(app: ShoppingApplication, place: CubeIntent) {
            val goAction = goActionMap[place.place?.placeName]
                ?: goActionMap[app.getRootPlace().placeName]

            goAction?.apply(app, place)
        }
    }

    // :: Repository delegate factories

    protected open fun createUserDelegate(delegate: UserRepository): UserRepository = delegate

    protected open fun createProductDelegate(delegate: ProductRepository): ProductRepository = delegate

    protected open fun createPurchaseDelegate(delegate: PurchaseRepository): PurchaseRepository = delegate

    protected open fun createPurchaseItemDelegate(delegate: PurchaseItemRepository): PurchaseItemRepository = delegate
}
