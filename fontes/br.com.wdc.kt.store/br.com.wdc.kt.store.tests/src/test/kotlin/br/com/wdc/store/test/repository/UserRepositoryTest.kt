package br.com.wdc.store.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.store.domain.security.PasswordUtil
import br.com.wdc.store.domain.user.User
import br.com.wdc.store.domain.user.UserCriteria
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.scripts.sgbd.DBReset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import br.com.wdc.store.test.util.TestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

class UserRepositoryTest : AbstractUserRepositoryTest() {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): UserRepository = env.userRepo

    // -- LOCAL mode only: through the REST API the password is never returned (see RestUserRepositoryTest) --

    private val digestProjection get() = User().apply { id = ProjectionValues.i64; password = ProjectionValues.str }

    @Test
    fun passwordDigest_comesOnlyWhenProjected() = runBlocking {
        val stored = repo().fetch(UserCriteria().withUserName("admin").withProjection(digestProjection)).single()
        // it is the digest, not the password; the column is CHAR(32), so it comes padded with spaces
        assertEquals("1ymiigxvce4vzea4zp5bsfbgj", stored.password!!.trim())
        assertNull(repo().fetch(UserCriteria().withUserName("admin")).single().password)
    }

    @Test
    fun seededDigests_areWhatTheApplicationComputes() = runBlocking {
        for (user in listOf("admin", "fulano", "beotrano")) {
            val stored = repo().fetch(UserCriteria().withUserName(user).withProjection(digestProjection)).single()
            assertEquals(PasswordUtil.hashPassword(user), stored.password!!.trim(), user)
        }
    }

    @Test
    fun update_defaultProjection_doesNotTouchThePassword() = runBlocking {
        val before = repo().fetchById(DBReset.FULANO_ID, digestProjection)!!.password

        val changes = User().apply { id = DBReset.FULANO_ID; userName = "fulano"; name = "Another Name"; roles = "CUSTOMER"; password = "must-not-be-written" }
        assertTrue(repo().update(changes))

        assertEquals(before, repo().fetchById(DBReset.FULANO_ID, digestProjection)!!.password)
    }

    @Test
    fun update_withThePasswordProjected_writesIt() = runBlocking {
        val changes = User().apply { id = DBReset.FULANO_ID; password = "new-digest" }
        assertTrue(repo().update(changes, null, User().apply { password = ProjectionValues.str }))
        assertEquals("new-digest", repo().fetchById(DBReset.FULANO_ID, digestProjection)!!.password!!.trim())
    }
}
