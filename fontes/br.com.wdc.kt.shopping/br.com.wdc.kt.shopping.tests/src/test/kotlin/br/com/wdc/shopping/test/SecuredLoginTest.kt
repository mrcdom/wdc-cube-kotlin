package br.com.wdc.shopping.test

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import br.com.wdc.shopping.domain.user.User
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
 * desafio, HMAC, JWT e a busca do nome de exibição pelo repositório com controle de acesso.
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
    fun afterLogin_aCustomerReachesOnlyTheirOwnUser_andNeverAPassword() = runBlocking {
        login("fulano")
        val projection = User().apply { id = ProjectionValues.i64; userName = ProjectionValues.str; password = ProjectionValues.str }
        val users = app.getUserRepository().fetch(UserCriteria().withProjection(projection))
        assertEquals(listOf<String?>("fulano"), users.map { it.userName })
        assertNull(users.single().password)
        assertNull(app.getUserRepository().fetchById(DBReset.ADMIN_ID))
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
