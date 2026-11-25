package br.com.wdc.store.test.repository

import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlinx.coroutines.runBlocking

/** The client logout revokes the session on the server: the refresh token stops being valid. */
class RestAuthLogoutTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-auth-logout", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun refreshStatus(refreshToken: String): Int {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${env.port}/api/auth/refresh"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"refreshToken":"$refreshToken"}"""))
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    @Test
    fun withoutLogout_theRefreshTokenWorks() {
        env.loginAs("fulano")
        assertEquals(200, refreshStatus(env.authClient!!.refreshToken!!))
        env.logout()
    }

    @Test
    fun logout_revokesTheRefreshToken() {
        env.loginAs("fulano")
        val client = env.authClient!!
        val refreshToken = client.refreshToken!!
        runBlocking { client.logout() }
        assertNull(client.refreshToken)
        assertEquals(401, refreshStatus(refreshToken), "after logout, the refresh token is no longer valid")
        env.logout()
    }
}
