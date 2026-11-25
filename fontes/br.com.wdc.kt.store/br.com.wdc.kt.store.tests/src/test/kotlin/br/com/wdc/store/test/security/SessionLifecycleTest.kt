package br.com.wdc.store.test.security

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.storage.JvmSessionStorage
import br.com.wdc.framework.domain.exception.UnauthenticatedException
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.SessionListener
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.open.login.LoginKeys
import br.com.wdc.store.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.store.test.mock.RootPresenterMock
import br.com.wdc.store.test.mock.viewimpl.LoginViewMock
import br.com.wdc.store.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Counts what a client hears about its session. */
private class HeardSession : SessionListener {
    var renewed = 0
    var lost = 0
    override fun sessionRenewed() { renewed++ }
    override fun sessionLost() { lost++ }
}

/**
 * The REST client's side of a session's life: a renewal replaces the refresh token and is heard; a renewal the server
 * refuses loses the session; signing out revokes it on the server.
 */
class RestSessionLifecycleTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-session-lifecycle", jwtSecret = "session-lifecycle-secret-0123456789-01")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    /** The server's own service: it renews and revokes sessions. */
    private val server: AuthenticationService get() = AuthenticationService.BEAN.get()

    @AfterEach
    fun signOut() = env.logout()

    /** Makes the next call present an access token the server does not accept: the effect of an expired one. */
    private fun expireAccessToken() {
        val client = env.authClient!!
        var stale = true
        val renew = env.transport.refreshHandler!!
        env.transport.accessTokenSupplier = { if (stale) "expired.access.token" else client.accessToken }
        env.transport.refreshHandler = { renew().also { if (it) stale = false } }
    }

    @Test
    fun aRenewal_replacesTheRefreshToken_andIsHeard(): Unit = runBlocking {
        env.loginAs("fulano")
        val client = env.authClient!!
        val heard = HeardSession().also { client.sessionListener = it }
        val oldRefreshToken = client.refreshToken!!

        expireAccessToken()
        assertEquals(4, env.productRepo.count(ProductCriteria()))

        assertEquals(1, heard.renewed)
        assertNotEquals(oldRefreshToken, client.refreshToken)
        assertNull(server.refresh(oldRefreshToken), "the old refresh token no longer works: what was kept must be replaced")
    }

    @Test
    fun aRenewalTheServerRefuses_losesTheSession(): Unit = runBlocking {
        env.loginAs("fulano")
        val client = env.authClient!!
        val heard = HeardSession().also { client.sessionListener = it }
        server.logout(client.refreshToken!!)

        expireAccessToken()
        assertFailsWith<UnauthenticatedException> { env.productRepo.count(ProductCriteria()) }

        assertEquals(1, heard.lost)
        assertEquals(0, heard.renewed)
        assertNull(client.accessToken, "the client forgot the session")
        assertNull(client.refreshToken)
    }

    @Test
    fun signingOut_revokesTheSessionOnTheServer() {
        env.loginAs("fulano")
        val client = env.authClient!!
        val refreshToken = client.refreshToken!!

        client.logout()

        assertNull(client.accessToken)
        assertNull(server.refresh(refreshToken), "the server no longer renews it")
    }
}

/**
 * The application's side: it keeps the session's tokens between runs each time they change, and when the session is
 * lost, the user goes to the login, told why, with the saved cart kept for after signing in.
 */
class AppSessionLifecycleTest {

    companion object {
        private val env = TestEnvironment("wedocode-store-app-session", jwtSecret = "app-session-secret-0123456789-0123456")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    /**
     * The server's service, seen as a client's: each written state is a new one, the listener is the test's to call,
     * and signing out is recorded.
     */
    private class ClientSideAuth(private val server: AuthenticationService) : AuthenticationService by server {
        var listener: SessionListener? = null
        var generation = 1
        var signedOut = false

        override fun writeAuthState(out: ExtensibleObjectOutput) {
            out.beginObject()
            out.name("generation").value(generation.toLong())
            out.endObject()
        }

        override fun setSessionListener(listener: SessionListener?) {
            this.listener = listener
        }

        override fun signOut() {
            signedOut = true
        }
    }

    private val storage = JvmSessionStorage()
    private val app = RootPresenterMock(storage)
    private val server = AuthenticationService.BEAN.get()
    private val auth = ClientSideAuth(server).also { AuthenticationService.BEAN.set(it) }

    @AfterEach
    fun tearDown() {
        AuthenticationService.BEAN.set(server)
        app.release()
    }

    private suspend fun signIn() {
        LoginKeys(app).flip()
        val login = LoginViewMock.cast(app.getRootView()!!.contentView)
        login.scope.userName = "fulano"
        login.scope.password = "fulano"
        login.scope.onEnter()
        RestrictedViewMock.cast(app.getRootView()!!.contentView)
    }

    /** Waits for the navigation the application started on its own. */
    private suspend fun untilTheLogin(): LoginViewMock = withTimeout(5_000) {
        while (app.getRootView()?.contentView !is LoginViewMock) delay(10)
        LoginViewMock.cast(app.getRootView()!!.contentView)
    }

    @Test
    fun signingIn_keepsTheSession_andEachRenewalReplacesTheKeptTokens(): Unit = runBlocking {
        signIn()
        assertNotNull(storage.getString(RootPresenter.SUBJECT_KEY))
        assertEquals("""{"generation":1}""", storage.getString(RootPresenter.AUTH_STATE_KEY))

        auth.generation = 2
        auth.listener!!.sessionRenewed()

        assertEquals("""{"generation":2}""", storage.getString(RootPresenter.AUTH_STATE_KEY))
    }

    @Test
    fun aLostSession_takesTheUserToTheLogin_toldWhy_keepingTheSavedCart(): Unit = runBlocking {
        signIn()
        storage.set(HomePresenter.CART_KEY, "the saved cart")

        auth.listener!!.sessionLost()

        val login = untilTheLogin()
        assertNull(app.subject)
        assertNull(storage.getString(RootPresenter.SUBJECT_KEY))
        assertNull(storage.getString(RootPresenter.AUTH_STATE_KEY))
        assertEquals("the saved cart", storage.getString(HomePresenter.CART_KEY))
        assertEquals("Your session has expired. Please sign in again.", login.scope.errorMessage)
        assertNull(app.scope.errorMessage, "no unexpected-error alert")
    }

    @Test
    fun signingOut_endsTheSessionOnTheServer_andForgetsEverything(): Unit = runBlocking {
        signIn()
        val home = RestrictedViewMock.cast(app.getRootView()!!.contentView)

        home.scope.onExit()

        assertTrue(auth.signedOut)
        assertNull(app.subject)
        assertNull(storage.getString(RootPresenter.SUBJECT_KEY))
        assertNull(storage.getString(RootPresenter.AUTH_STATE_KEY))
        LoginViewMock.cast(app.getRootView()!!.contentView)
    }
}
