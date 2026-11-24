package br.com.wdc.store.presentation.presenter

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.framework.cube.PresenterBase
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.presentation.presenter.open.OpenKeys
import br.com.wdc.store.presentation.presenter.open.login.LoginKeys
import br.com.wdc.store.presentation.presenter.restricted.RestrictedKeys
import br.com.wdc.store.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.store.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.store.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import br.com.wdc.store.presentation.presenter.open.login.structs.Subject
import br.com.wdc.store.presentation.presenter.restricted.cart.CartManager

/**
 * The root presenter of the WDC Store: the root of its place tree ([RootKeys.PLACE] is [CubeTreePlace.ROOT]). It owns
 * the page frame — the slot where the first level shows its view, and the unexpected-error alert ([state]) — restores
 * the session on its first navigation, and takes part in every navigation before the first place
 * ([applyParameters]); it is the presenter of the root view ([PresenterBase]), so the view layers paint it like any
 * other. It extends [CubeApplication] because the root presenter is what holds the whole structure up: the presenters
 * alive, the navigation, the places an address can name ([PLACES]). Each platform extends it with what it needs (session
 * storage, history).
 */
abstract class RootPresenter : CubeApplication(PLACES), PresenterBase {

    /**
     * Single-threaded coroutine scope for presenter actions.
     * limitedParallelism(1) guarantees serial execution per application instance,
     * while allowing different application instances to run in parallel.
     */
    val presenterScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    val sessionStorage: SessionStorage by lazy { createSessionStorage() }

    var subject: Subject? = null

    var cart: CartManager? = null

    // :: The page frame

    /** What the root view shows: the view of the first level, and the unexpected-error alert. */
    val scope = RootScope().also { it.observe(::update) }

    /** The root view: the view the catalog gives for [scope] ([scopeViews]), created on the first navigation. */
    val view: CubeView? get() = viewOrNull(scope)

    private val contentSlot = ScopeSlot { if (scope.content !== it) scope.content = it }

    override val app: CubeApplication get() = this

    /** Asks the root view to repaint. */
    fun update() {
        viewOrNull(scope)?.update()
    }

    /** Both the application (every presenter's) and the root presenter (none of its own) have one. */
    override fun commitComputedState() {
        super<CubeApplication>.commitComputedState()
    }

    // :: Getters and Setters

    fun getRootPlace(): CubeTreePlace = RootKeys.PLACE


    // :: API

    /** Logs the failure with its stack trace and shows the user only what failed and the cause's message. */
    override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        scope.errorMessage = errorText(message, e)
        logger.error(scope.errorMessage!!, e)
    }

    /** The text the user sees for an unexpected error: what failed, and the cause's message when there is one. */
    protected fun errorText(message: String, e: Throwable): String =
        if (e.message.isNullOrBlank()) message else "$message: ${e.message}"

    /**
     * The application's part in every navigation: on the first one, creates the root view and restores the session;
     * the root alone shows nothing, so it goes on to the restricted area, with the intent in transit; otherwise, offers the slot
     * of the first level.
     */
    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (initialization) {
            viewOf(scope)
            restoreSubjectFromSession()
        }

        if (deepest) {
            // the root alone shows nothing: the restricted area decides (the home, or the login without a user)
            RestrictedKeys(this, intent).flip()
            return false
        }
        RootKeys(this, intent).parentSlot = contentSlot
        return true
    }

    override fun release() {
        scope.content = null
        super.release()
        releaseView(scope)
    }

    companion object {
        private val LOG = Log.getLogger("RootPresenter")

        /**
         * Every place an address can name, given to [CubeApplication], which resolves addresses among them. Listed
         * by hand: each keys class declares its own place, and a place is created when its keys are first used —
         * this list creates them all before the first navigation. The root (the application itself) is not in it:
         * an address that names no place goes to the root, which goes on to the home.
         */
        val PLACES: List<CubeTreePlace> = listOf(
            OpenKeys.PLACE,
            LoginKeys.PLACE,
            RestrictedKeys.PLACE,
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

        restoreAuthStateFromSession()
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
