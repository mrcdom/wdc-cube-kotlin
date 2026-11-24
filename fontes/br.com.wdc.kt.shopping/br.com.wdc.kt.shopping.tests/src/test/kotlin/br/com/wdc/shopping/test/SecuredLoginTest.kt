package br.com.wdc.shopping.test

import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.presentation.presenter.open.login.calls.CredentialsCheck
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Login through the presentation layer **with security on**, as the remote view server runs it:
 * challenge, HMAC, JWT and the lookup of the display name.
 */
class SecuredLoginTest {

    companion object {
        private val env = TestEnvironment("wedocode-shopping-secured-login", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clear()
    }

    private fun login(userName: String, password: String = userName) = runBlocking { CredentialsCheck(userName, password).run() }

    @Test
    fun admin_logsIn() {
        val subject = login("admin")
        assertEquals(DBReset.ADMIN_ID, subject!!.id)
        assertEquals("John Smith", subject.nickName)
    }

    @Test
    fun customer_logsIn() {
        val subject = login("fulano")
        assertEquals(DBReset.FULANO_ID, subject!!.id)
        assertEquals("John Doe", subject.nickName)
    }

    @Test
    fun customerWhoseDigestHasTheSignBit_logsIn() {
        val subject = login("beotrano")
        assertEquals(DBReset.BEOTRANO_ID, subject!!.id)
    }

    @Test
    fun repositoriesInsideTheServer_areNotRestricted_theApiIs() = runBlocking {
        // Inside the server, the caller of the repository is the presentation, and it is the one that asks only for
        // what belongs to the user. Access control lives at the HTTP boundary (see the SecuredRest* tests).
        login("fulano")
        assertEquals(3, UserRepository.BEAN.get().count(UserCriteria()))
        assertNotNull(UserRepository.BEAN.get().fetchById(DBReset.ADMIN_ID))
    }

    @Test
    fun wrongPassword_isRefused() {
        assertNull(login("admin", "wrong-password"))
    }

    @Test
    fun unknownUser_isRefused() {
        assertNull(login("ninguem", "ninguem"))
    }
}
