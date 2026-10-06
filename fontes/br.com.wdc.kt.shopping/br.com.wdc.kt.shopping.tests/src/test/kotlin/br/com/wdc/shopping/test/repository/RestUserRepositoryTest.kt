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
 * Executa os mesmos testes de [UserRepositoryTest], porém pelo caminho REST:
 * client HTTP → Javalin in-process → repositórios de persistência → H2.
 */
class RestUserRepositoryTest : AbstractUserRepositoryTest() {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-user")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): UserRepository = env.userRepo

    // -- Só no modo REST --

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
    fun password_isWriteOnly() = runBlocking {
        val user = User().apply { userName = "gravado"; password = "resumo"; name = "Gravado" }
        assertTrue(repo().insert(user))
        assertTrue(repo().update(User().apply { id = user.id; password = "outro" }, null, User().apply { password = ProjectionValues.str }))
        assertNull(repo().fetchById(user.id!!)!!.password)
    }
}
