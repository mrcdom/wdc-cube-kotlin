package br.com.wdc.shopping.presentation

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.framework.cube.PresenterBase
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.SimpleSecurityContext
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.RootViewState
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
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager

/**
 * The Storefront application, and the root of its place tree ([RootKeys.PLACE] is [CubeTreePlace.ROOT]): it owns the
 * page frame — the slot where the first level shows its view, and the unexpected-error alert ([state]) — restores the
 * session on its first navigation, and takes part in every navigation before the first place
 * ([applyParameters]). It is the presenter of the root view ([PresenterBase]), so the view layers paint it like any
 * other.
 */
abstract class ShoppingApplication : CubeApplication(PLACES), PresenterBase {

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

    // :: The page frame

    /** What the root view shows: the view of the first level, and the unexpected-error alert. */
    val state = RootViewState().also { it.observe(::update) }

    /** The root view, created on the first navigation by [createView]. */
    var view: CubeView? = null
        private set

    private val contentSlot = CubeViewSlot { v -> if (state.contentView !== v) state.contentView = v }

    override val app: CubeApplication get() = this

    /** Asks the root view to repaint. */
    fun update() {
        view?.update()
    }

    // :: Getters and Setters

    fun getRootPlace(): CubeTreePlace = RootKeys.PLACE

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
        state.errorMessage = if (e.message.isNullOrBlank()) message else "$message: ${e.message}"
        logger.error(state.errorMessage!!, e)
    }

    /**
     * The application's part in every navigation: on the first one, creates the root view and restores the session;
     * the root alone shows nothing, so it goes on to the home, with the intent in transit; otherwise, offers the slot
     * of the first level.
     */
    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (initialization) {
            view = createView?.invoke(this)
            restoreSubjectFromSession()
        }

        if (deepest) {
            HomeKeys(this, intent).flip()
            return false
        }
        RootKeys(this, intent).parentSlot = contentSlot
        return true
    }

    override fun release() {
        state.contentView = null
        view?.release()
        view = null
        super.release()
    }

    companion object {
        private val LOG = Log.getLogger("ShoppingApplication")

        /** Creates the root view; registered by each entry point, like the presenters' `createView`. */
        var createView: ((ShoppingApplication) -> CubeView)? = null

        /**
         * Every place an address can name, given to [CubeApplication], which resolves addresses among them. Listed
         * by hand: each keys class declares its own place, and a place is created when its keys are first used —
         * this list creates them all before the first navigation. The root (the application itself) is not in it:
         * an address that names no place goes to the root, which goes on to the home.
         */
        val PLACES: List<CubeTreePlace> = listOf(
            LoginKeys.PLACE,
            HomeKeys.PLACE,
            CartKeys.PLACE,
            ProductKeys.PLACE,
            ReceiptKeys.PLACE,
        )
    }

    protected abstract fun createSessionStorage(): SessionStorage

    // :: Session

    private fun restoreSubjectFromSession() {
        if (subject != null) return

        val json = sessionStorage.getString("subject") ?: return
        try {
            val inp = JsonInputFactory.createStringInput(json).input
            val stored = Subject()
            stored.readExternal(inp)
            if (stored.id != null) {
                subject = stored
            }
        } catch (e: Exception) {
            LOG.error("restoreSubjectFromSession", e)
            sessionStorage.remove("subject")
        }

        restoreSecurityContextFromSession()
        restoreAuthStateFromSession()
    }

    private fun restoreSecurityContextFromSession() {
        if (securityContext != null) return

        val json = sessionStorage.getString("securityContext") ?: return
        try {
            val inp = JsonInputFactory.createStringInput(json).input
            val ctx = SimpleSecurityContext()
            ctx.readExternal(inp)
            if (ctx.userId != null) {
                securityContext = ctx
            }
        } catch (e: Exception) {
            LOG.error("restoreSecurityContextFromSession", e)
            sessionStorage.remove("securityContext")
        }
    }

    private fun restoreAuthStateFromSession() {
        val json = sessionStorage.getString("authState") ?: return
        try {
            val authService = AuthenticationService.BEAN.getOrNull() ?: return
            val inp = JsonInputFactory.createStringInput(json).input
            authService.readAuthState(inp)
        } catch (e: Exception) {
            LOG.error("restoreAuthStateFromSession", e)
            sessionStorage.remove("authState")
        }
    }
}
