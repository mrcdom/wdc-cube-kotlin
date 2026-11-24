package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.exception.AccessDeniedException
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

/**
 * User over the REST path with security on: the user data policy (rows, fields, masks) and the password that never
 * leaves the server.
 */
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
        assertTrue(e.message!!.startsWith("HTTP $status"), "expected HTTP $status, got: ${e.message}")
    }

    private fun withPassword() = User().apply { id = ProjectionValues.i64; userName = ProjectionValues.str; password = ProjectionValues.str }

    private fun login() = User().apply { id = ProjectionValues.i64; userName = ProjectionValues.str; name = ProjectionValues.str }

    /** Refused with 403, naming [field]. */
    private fun assertDeniedNaming(field: String, block: suspend () -> Unit) {
        val e = assertThrows(AccessDeniedException::class.java) { runBlocking { block() } }
        assertTrue("[$field]" in e.message!!, e.message)
    }

    /** A manager, created by the administrator, and signed in. */
    private fun signInAsManager(): Long = runBlocking {
        env.loginAs("admin")
        val manager = User().apply { userName = "manager"; password = env.authClient!!.encryptPassword("m4nager"); name = "Mary Major"; roles = "MANAGER" }
        assertTrue(repo().insert(manager))
        env.loginAs("manager", "m4nager")
        manager.id!!
    }

    @Test
    fun withoutAuthentication_isRefused() {
        assertHttp(401) { repo().fetch(UserCriteria()) }
        assertHttp(401) { repo().insert(User().apply { userName = "x"; password = "p"; name = "X" }) }
    }

    @Test
    fun admin_seesEveryUser_butNeverThePassword() = runBlocking {
        env.loginAs("admin")

        val users = repo().fetch(UserCriteria().withOrderBy(UserCriteria.OrderBy.LOGIN_A_TO_Z).withProjection(login()))
        assertEquals(listOf<String?>("admin", "beotrano", "fulano"), users.map { it.userName })

        // the password is in no read grant: asking for it is refused, naming it
        assertDeniedNaming("password") { repo().fetch(UserCriteria().withProjection(withPassword())) }
        assertDeniedNaming("password") { repo().fetchById(DBReset.FULANO_ID, withPassword()) }
        assertDeniedNaming("password") { repo().fetchPage(UserCriteria().withProjection(withPassword()), 0, 10) }

        // the default shape: every field the role reads — not the password
        val all = repo().fetch(UserCriteria())
        assertEquals(3, all.size)
        assertTrue(all.all { it.password == null && it.userName != null && it.roles != null })
    }

    @Test
    fun admin_writesUsers() = runBlocking {
        env.loginAs("admin")

        val user = User().apply { userName = "newbie"; password = env.authClient!!.encryptPassword("s3cret"); name = "Newbie"; roles = "CUSTOMER" }
        assertTrue(repo().insert(user))
        assertTrue(repo().update(User().apply { id = user.id; name = "New Name" }, null, User().apply { name = ProjectionValues.str }))
        assertEquals("New Name", repo().fetchById(user.id!!)!!.name)
        assertEquals(1, repo().delete(UserCriteria().withUserId(user.id)))
    }

    @Test
    fun userCreatedThroughTheApi_logsInWithTheirPassword() = runBlocking {
        env.loginAs("admin")
        // the client encrypts the password with the session's public key; the server stores its digest
        val user = User().apply { userName = "newcomer"; password = env.authClient!!.encryptPassword("s3cret"); name = "Newcomer"; roles = "CUSTOMER" }
        assertTrue(repo().insert(user))
        env.loginAs("newcomer", "s3cret")
        assertEquals(listOf<String?>("newcomer"), repo().fetch(UserCriteria()).map { it.userName })

        env.loginAs("admin")
        val update = User().apply { id = user.id; password = env.authClient!!.encryptPassword("changed") }
        assertTrue(repo().update(update, null, User().apply { password = ProjectionValues.str }))
        env.loginAs("newcomer", "changed")
    }

    @Test
    fun passwordNotEncryptedWithTheSessionKey_isRefused() = runBlocking {
        env.loginAs("admin")
        assertHttp(400) { repo().insert(User().apply { userName = "plain"; password = "s3cret"; name = "Plain"; roles = "CUSTOMER" }) }
        assertEquals(emptyList<User>(), repo().fetch(UserCriteria().withUserName("plain")))
        assertHttp(400) { repo().update(User().apply { id = DBReset.FULANO_ID; password = "s3cret" }, null, User().apply { password = ProjectionValues.str }) }
        // fulano's password is untouched
        env.loginAs("fulano")
    }

    @Test
    fun customer_readsOnlyTheirOwnUser_andCannotWrite() = runBlocking {
        env.loginAs("fulano")

        // the policy restricts to the user themselves, whatever is asked
        assertEquals(listOf<String?>("fulano"), repo().fetch(UserCriteria().withProjection(login())).map { it.userName })
        assertEquals(1, repo().count(UserCriteria()))
        assertEquals(emptyList<User>(), repo().fetch(UserCriteria().withUserName("admin")))
        assertNull(repo().fetchById(DBReset.ADMIN_ID))
        assertEquals("John Doe", repo().fetchById(DBReset.FULANO_ID)!!.name)
        assertDeniedNaming("password") { repo().fetchById(DBReset.FULANO_ID, withPassword()) }

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
        // an old client that still sends "password" in the criteria does not filter by it: the field is ignored
        val response = env.transport.postJson("/api/repo/user/count", """{"userName":"admin","password":"anything"}""")
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
        val requests = listOf(
            "/api/repo/user/fetch" to """{"projection":{"id":1,"userName":"~","password":"~"}}""",
            "/api/repo/user/fetch-page" to """{"page":0,"pageSize":10,"projection":{"password":"~"}}""",
            "/api/repo/user/fetch-by-id" to """{"id":${DBReset.ADMIN_ID},"projection":{"password":"~"}}""",
        )
        for ((path, body) in requests) {
            val e = assertThrows(AccessDeniedException::class.java) { env.transport.postJson(path, body) }
            assertTrue(e.message!!.startsWith("HTTP 403") && "[password]" in e.message!!, e.message)
        }
        val bodies = listOf(
            env.transport.postJson("/api/repo/user/fetch", "{}"),
            env.transport.postJson("/api/repo/user/fetch-page", """{"page":0,"pageSize":10}"""),
            env.transport.postJson("/api/repo/user/fetch-by-id", """{"id":${DBReset.ADMIN_ID}}"""),
        )
        for (body in bodies) {
            assertTrue("admin" in body, body)
            assertFalse("password" in body, body)
        }
        // nor the insert's answer, which carries the entity as stored
        val inserted = env.transport.postJson(
            "/api/repo/user/insert",
            """{"entity":{"userName":"raw","password":"${env.authClient!!.encryptPassword("s3cret")}","name":"Raw","roles":"CUSTOMER"}}""",
        )
        assertTrue("\"userName\":\"raw\"" in inserted, inserted)
        assertFalse("password" in inserted, inserted)
    }

    // :: Manager

    @Test
    fun manager_readsTheirOwnUserInFull_andTheOthersByName_withTheLoginMasked() = runBlocking {
        val managerId = signInAsManager()

        val users = repo().fetch(UserCriteria().withOrderBy(UserCriteria.OrderBy.OLDEST_FIRST).withProjection(login()))
        assertEquals(4, users.size)
        val byId = users.associateBy { it.id }
        assertEquals("manager", byId[managerId]!!.userName)
        assertEquals("f*****", byId[DBReset.FULANO_ID]!!.userName)
        assertEquals("a****", byId[DBReset.ADMIN_ID]!!.userName)
        assertEquals("John Doe", byId[DBReset.FULANO_ID]!!.name)

        // the roles are read only of their own user; the default shape is cut to that, not refused
        val shaped = repo().fetch(UserCriteria()).associateBy { it.id }
        assertEquals("MANAGER", shaped[managerId]!!.roles)
        assertNull(shaped[DBReset.FULANO_ID]!!.roles)
        assertEquals("f*****", shaped[DBReset.FULANO_ID]!!.userName)

        // filtering or sorting by the login reveals it: only the grant that reads it as it is — their own user — applies
        assertEquals(emptyList<User>(), repo().fetch(UserCriteria().withUserName("fulano")))
        assertEquals(listOf(managerId), repo().fetch(UserCriteria().withOrderBy(UserCriteria.OrderBy.LOGIN_A_TO_Z)).map { it.id })
        assertEquals(4, repo().count(UserCriteria()))

        // and writes nothing
        assertHttp(403) { repo().update(User().apply { id = managerId; name = "Renamed" }, null, User().apply { name = ProjectionValues.str }) }
        assertHttp(403) { repo().delete(UserCriteria().withUserId(DBReset.FULANO_ID)) }
    }
}
