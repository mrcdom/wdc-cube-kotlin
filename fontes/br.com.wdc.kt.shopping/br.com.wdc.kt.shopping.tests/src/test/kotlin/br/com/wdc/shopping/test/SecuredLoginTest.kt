package br.com.wdc.shopping.test

import br.com.wdc.shopping.domain.security.SecurityContextHolder
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.presentation.presenter.open.login.LoginService
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * O login pela camada de apresentação **com a segurança ligada**, como o servidor da view remota o executa:
 * desafio, HMAC, JWT e a busca do nome de exibição.
 */
class SecuredLoginTest {

    companion object {
        private val env = TestEnvironment("wedocode-shopping-secured-login", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private val app = ShoppingApplicationMock()

    @AfterEach
    fun tearDown() {
        app.release()
        SecurityContextHolder.clear()
    }

    private fun login(userName: String, password: String = userName) = runBlocking { LoginService(app).fetchSubject(userName, password) }

    @Test
    fun admin_logsIn() {
        val subject = login("admin")
        assertEquals(DBReset.ADMIN_ID, subject!!.id)
        assertEquals("João da Silva", subject.nickName)
    }

    @Test
    fun customer_logsIn() {
        val subject = login("fulano")
        assertEquals(DBReset.FULANO_ID, subject!!.id)
        assertEquals("Fulano de Tal", subject.nickName)
    }

    @Test
    fun customerWhoseDigestHasTheSignBit_logsIn() {
        val subject = login("beotrano")
        assertEquals(DBReset.BEOTRANO_ID, subject!!.id)
    }

    @Test
    fun afterLogin_theApplicationHoldsTheContextOfTheSession() {
        login("fulano")
        val context = app.getSecurityContext()!!
        assertEquals(DBReset.FULANO_ID, context.userId)
        assertFalse(context.hasDataAll())
        assertTrue(context.hasPermission("purchase", "write"))
        assertFalse(context.hasPermission("product", "write"))
    }

    @Test
    fun repositoriesInsideTheServer_areNotRestricted_theApiIs() = runBlocking {
        // Dentro do servidor, quem chama o repositório é a apresentação, e é ela que pede só o que cabe ao
        // usuário. O controle de acesso fica na fronteira HTTP (ver os testes SecuredRest*).
        login("fulano")
        assertEquals(3, app.getUserRepository().count(UserCriteria()))
        assertNotNull(app.getUserRepository().fetchById(DBReset.ADMIN_ID))
    }

    @Test
    fun wrongPassword_isRefused() {
        assertNull(login("admin", "senha-errada"))
    }

    @Test
    fun unknownUser_isRefused() {
        assertNull(login("ninguem", "ninguem"))
    }
}
