package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.scripts.sgbd.DBReset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Runs the same tests as [UserRepositoryTest], but over the REST path:
 * HTTP client → in-process Javalin → persistence repositories → H2.
 */
class RestUserRepositoryTest : AbstractUserRepositoryTest() {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-user")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): UserRepository = env.userRepo

    // -- REST mode only --

    @Test
    fun password_isNeverReturned_evenWithoutSecurity_evenWhenProjected() = runBlocking {
        val projection = User().apply { id = ProjectionValues.i64; userName = ProjectionValues.str; password = ProjectionValues.str }

        assertTrue(repo().fetch(UserCriteria().withProjection(projection)).all { it.password == null })
        assertNull(repo().fetchById(DBReset.ADMIN_ID, projection)!!.password)
        assertTrue(repo().fetchPage(UserCriteria().withProjection(projection), 0, 10).items.all { it.password == null })

        val raw = env.transport.postJson("/api/repo/user/fetch", """{"projection":{"userName":"~","password":"~"}}""")
        assertFalse("password" in raw, raw)
    }

    @Test
    fun password_isWriteOnly_andStoredAsItsDigest() = runBlocking {
        val user = User().apply { userName = "stored"; password = "s3cret"; name = "Stored" }
        assertTrue(repo().insert(user))
        assertNull(repo().fetchById(user.id!!)!!.password)
        // without security there is no session key: the password comes as typed, and the server stores its digest
        assertEquals(DBReset.passwordDigest("s3cret"), storedPassword(user.id!!))

        assertTrue(repo().update(User().apply { id = user.id; password = "other" }, null, User().apply { password = ProjectionValues.str }))
        assertNull(repo().fetchById(user.id!!)!!.password)
        assertEquals(DBReset.passwordDigest("other"), storedPassword(user.id!!))
    }

    /** The password column as the server stored it, read by the server-side repository (fixed width: padded). */
    private suspend fun storedPassword(id: Long): String? =
        UserRepository.BEAN.get().fetchById(id, User().apply { this.id = ProjectionValues.i64; password = ProjectionValues.str })?.password?.trim()
}
