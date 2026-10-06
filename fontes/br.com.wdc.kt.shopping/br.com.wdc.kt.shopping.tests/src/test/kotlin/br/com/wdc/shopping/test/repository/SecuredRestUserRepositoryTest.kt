package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Usuário pelo caminho REST com a segurança ligada: permissão, escopo e a senha que nunca sai do servidor. */
class SecuredRestUserRepositoryTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-secured-user", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun repo(): UserRepository = env.userRepo

    @AfterEach
    fun signOut() = env.logout()

    private fun assertHttp(status: Int, block: suspend () -> Unit) {
        val e = assertThrows(RuntimeException::class.java) { runBlocking { block() } }
        assertTrue(e.message!!.startsWith("HTTP $status"), "esperava HTTP $status, veio: ${e.message}")
    }

    private fun withPassword() = User().apply { id = ProjectionValues.i64; userName = ProjectionValues.str; password = ProjectionValues.str }

    @Test
    fun withoutAuthentication_isRefused() {
        assertHttp(401) { repo().fetch(UserCriteria()) }
        assertHttp(401) { repo().insert(User().apply { userName = "x"; password = "p"; name = "X" }) }
    }

    @Test
    fun admin_seesEveryUser_butNeverThePassword() = runBlocking {
        env.loginAs("admin")

        val users = repo().fetch(UserCriteria().withOrderBy(UserCriteria.OrderBy.LOGIN_A_TO_Z).withProjection(withPassword()))
        assertEquals(listOf<String?>("admin", "beotrano", "fulano"), users.map { it.userName })
        assertTrue(users.all { it.password == null }, "a senha saiu do servidor")

        assertNull(repo().fetchById(DBReset.FULANO_ID, withPassword())!!.password)
        assertTrue(repo().fetchPage(UserCriteria().withProjection(withPassword()), 0, 10).items.all { it.password == null })
    }

    @Test
    fun admin_writesUsers() = runBlocking {
        env.loginAs("admin")

        val user = User().apply { userName = "novo"; password = "digest"; name = "Novo"; roles = "CUSTOMER" }
        assertTrue(repo().insert(user))
        assertTrue(repo().update(User().apply { id = user.id; name = "Novo Nome" }, null, User().apply { name = ProjectionValues.str }))
        assertEquals("Novo Nome", repo().fetchById(user.id!!)!!.name)
        assertEquals(1, repo().delete(UserCriteria().withUserId(user.id)))
    }

    @Test
    fun customer_readsOnlyTheirOwnUser_andCannotWrite() = runBlocking {
        env.loginAs("fulano")

        // o escopo restringe ao próprio usuário, peça o que pedir
        assertEquals(listOf<String?>("fulano"), repo().fetch(UserCriteria().withProjection(withPassword())).map { it.userName })
        assertEquals(1, repo().count(UserCriteria()))
        assertEquals(emptyList<User>(), repo().fetch(UserCriteria().withUserName("admin")))
        assertNull(repo().fetchById(DBReset.ADMIN_ID))
        assertEquals("Fulano de Tal", repo().fetchById(DBReset.FULANO_ID)!!.name)
        assertNull(repo().fetchById(DBReset.FULANO_ID, withPassword())!!.password)

        assertHttp(403) { repo().insert(User().apply { userName = "x"; password = "p"; name = "X" }) }
        assertHttp(403) { repo().update(User().apply { id = DBReset.FULANO_ID; roles = "ADMIN" }, null, User().apply { roles = ProjectionValues.str }) }
        assertHttp(403) { repo().delete(UserCriteria().withUserId(DBReset.BEOTRANO_ID)) }
        assertEquals("CUSTOMER", repo().fetchById(DBReset.FULANO_ID)!!.roles)
    }

    @Test
    fun unknownOrderBy_is400() {
        env.loginAs("admin")
        val e = assertThrows(BusinessException::class.java) { env.transport.postJson("/api/repo/user/fetch", """{"orderBy":"RANDOM"}""") }
        assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
        assertTrue("OLDEST_FIRST, NEWEST_FIRST, NAME_A_TO_Z, LOGIN_A_TO_Z" in e.message!!, e.message)
    }

    @Test
    fun passwordIsNotACriteriaField() {
        env.loginAs("admin")
        // um cliente antigo que ainda mande "password" no critério não filtra por ela: o campo é ignorado
        val response = env.transport.postJson("/api/repo/user/count", """{"userName":"admin","password":"qualquer"}""")
        assertEquals("""{"count":1}""", response)
    }

    @Test
    fun restLogin_worksForEverySeededUser() {
        for (user in listOf("admin", "fulano", "beotrano")) {
            env.loginAs(user)
            env.logout()
        }
    }

    @Test
    fun rawResponses_neverCarryAPasswordKey() {
        env.loginAs("admin")
        val bodies = listOf(
            env.transport.postJson("/api/repo/user/fetch", """{"projection":{"id":1,"userName":"~","password":"~"}}"""),
            env.transport.postJson("/api/repo/user/fetch-page", """{"page":0,"pageSize":10,"projection":{"password":"~"}}"""),
            env.transport.postJson("/api/repo/user/fetch-by-id", """{"id":${DBReset.ADMIN_ID},"projection":{"password":"~"}}"""),
        )
        for (body in bodies) {
            assertFalse("password" in body, body)
        }
    }
}
