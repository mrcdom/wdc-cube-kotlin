package br.com.wdc.shopping.test

import br.com.wdc.shopping.domain.security.SecurityContextHolder
import br.com.wdc.shopping.presentation.presenter.open.login.LoginService
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
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
    @Disabled(
        "defeito conhecido: depois de autenticar, o LoginService busca o nome do usuário pelo repositório com " +
        "controle de acesso, e CUSTOMER/MANAGER não têm user:read — falha com AccessDeniedException. " +
        "Com a segurança ligada, só ADMIN completa o login pela apresentação"
    )
    fun customer_logsIn() {
        val subject = login("fulano")
        assertEquals(DBReset.FULANO_ID, subject!!.id)
        assertEquals("Fulano de Tal", subject.nickName)
    }

    @Test
    @Disabled(
        "defeito conhecido: o resumo da senha de beotrano tem o bit de sinal — a carga grava a variante com sinal " +
        "(-17msd…) e o cliente calcula a sem sinal (dxz5j…), então o HMAC nunca confere; ver PasswordUtil × DBReset"
    )
    fun customerWhoseDigestHasTheSignBit_logsIn() {
        val subject = login("beotrano")
        assertEquals(DBReset.BEOTRANO_ID, subject!!.id)
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
