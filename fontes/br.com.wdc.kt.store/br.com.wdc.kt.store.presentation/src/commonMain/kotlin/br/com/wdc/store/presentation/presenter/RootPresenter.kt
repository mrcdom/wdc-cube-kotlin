package br.com.wdc.store.presentation.presenter

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.framework.cube.PresenterBase
import br.com.wdc.framework.domain.exception.UnauthenticatedException
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.SessionListener
import br.com.wdc.store.presentation.presenter.open.OpenKeys
import br.com.wdc.store.presentation.presenter.open.login.LoginKeys
import br.com.wdc.store.presentation.presenter.restricted.RestrictedKeys
import br.com.wdc.store.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.store.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.store.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import br.com.wdc.store.presentation.presenter.open.login.structs.Subject
import br.com.wdc.store.presentation.presenter.restricted.cart.CartManager

/**
 * The root presenter of the WDC Store: the root of its place tree ([RootKeys.PLACE] is [CubeTreePlace.ROOT]). It owns
 * the page frame — the slot where the first level shows its view, and the unexpected-error alert ([scope]) — restores
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

    /** What the root view shows: the view of the first level, and the unexpected-error alert. Each view layer draws it. */
    val scope = RootScope()

    private val contentSlot = ScopeSlot { if (scope.content !== it) scope.content = it }

    override val app: CubeApplication get() = this

    /** Both the application (every presenter's) and the root presenter (none of its own) have one. */
    override fun commitComputedState() {
        super<CubeApplication>.commitComputedState()
    }

    // :: Getters and Setters

    fun getRootPlace(): CubeTreePlace = RootKeys.PLACE


    // :: API

    /**
     * Logs the failure with its stack trace and shows the user only what failed and the cause's message. A call
     * refused because the session was lost is not shown: the user is already on the way to the login
     * ([sessionListener]).
     */
    override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        if (e is UnauthenticatedException && subject == null) {
            logger.info("{}: the session was lost", message)
            return
        }
        scope.errorMessage = errorText(message, e)
        logger.error(scope.errorMessage!!, e)
    }

    /** The text the user sees for an unexpected error: what failed, and the cause's message when there is one. */
    protected fun errorText(message: String, e: Throwable): String =
        if (e.message.isNullOrBlank()) message else "$message: ${e.message}"

    /**
     * The application's part in every navigation: on the first one, restores the session;
     * the root alone shows nothing, so it goes on to the restricted area, with the intent in transit; otherwise, offers the slot
     * of the first level.
     */
    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (initialization) {
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


    companion object {
        private val LOG = Log.getLogger("RootPresenter")

        /** Where [sessionStorage] keeps the signed-in user. */
        const val SUBJECT_KEY = "subject"

        /** Where [sessionStorage] keeps the session's tokens ([AuthenticationService.writeAuthState]). */
        const val AUTH_STATE_KEY = "authState"

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

    /**
     * Deciphers what a remote view sent ciphered with the session's key (the password typed at the login). Only the
     * remote host has that key: in an app whose views run in the same process, nothing comes ciphered, and nothing
     * calls this.
     */
    open fun b64Decipher(b64Text: String): String =
        throw UnsupportedOperationException("Only a remote view sends ciphered values")

    // :: Session

    /**
     * The user signed in: keeps who it is and the session's tokens, here and between runs ([sessionStorage]), and
     * listens to the session from now on.
     */
    fun startSession(subject: Subject) {
        this.subject = subject
        val out = JsonOutputFactory.createStringOutput()
        subject.writeExternal(out.output)
        sessionStorage.set(SUBJECT_KEY, out.resultString())
        saveAuthState()
        AuthenticationService.BEAN.getOrNull()?.setSessionListener(sessionListener)
    }

    /** The user signs out: the server revokes the session, and nothing of it stays here, not even the saved cart. */
    fun endSession() {
        AuthenticationService.BEAN.getOrNull()?.signOut()
        subject = null
        sessionStorage.clear()
    }

    /**
     * The session's tokens change when the client renews it, and the old refresh token stops working: the ones kept
     * between runs are replaced each time. When the server will not renew the session, the user is forgotten and
     * goes to the login, with a word on why; the saved cart stays, for the same user to find after signing in.
     */
    private val sessionListener = object : SessionListener {

        override fun sessionRenewed() {
            if (subject != null) saveAuthState()
        }

        override fun sessionLost() {
            if (subject == null) return
            LOG.info("The session of user {} was lost", subject?.id)
            subject = null
            sessionStorage.remove(SUBJECT_KEY)
            sessionStorage.remove(AUTH_STATE_KEY)
            // after the call that found it out: that one fails, and this navigation waits for it
            presenterScope.launch {
                LoginKeys(this@RootPresenter).apply { sessionExpired = true }.flip()
            }
        }
    }

    private fun saveAuthState() {
        val authService = AuthenticationService.BEAN.getOrNull() ?: return
        val out = JsonOutputFactory.createStringOutput()
        authService.writeAuthState(out.output)
        sessionStorage.set(AUTH_STATE_KEY, out.resultString())
    }

    private fun restoreSubjectFromSession() {
        if (subject != null) return

        val json = sessionStorage.getString(SUBJECT_KEY) ?: return
        try {
            val inp = JsonInputFactory.createStringInput(json).input
            val stored = Subject()
            stored.readExternal(inp)
            if (stored.id != null) {
                subject = stored
            }
        } catch (e: Exception) {
            LOG.error("restoreSubjectFromSession", e)
            sessionStorage.remove(SUBJECT_KEY)
        }

        restoreAuthStateFromSession()
        if (subject != null) {
            AuthenticationService.BEAN.getOrNull()?.setSessionListener(sessionListener)
        }
    }

    private fun restoreAuthStateFromSession() {
        val json = sessionStorage.getString(AUTH_STATE_KEY) ?: return
        try {
            val authService = AuthenticationService.BEAN.getOrNull() ?: return
            val inp = JsonInputFactory.createStringInput(json).input
            authService.readAuthState(inp)
        } catch (e: Exception) {
            LOG.error("restoreAuthStateFromSession", e)
            sessionStorage.remove(AUTH_STATE_KEY)
        }
    }
}
