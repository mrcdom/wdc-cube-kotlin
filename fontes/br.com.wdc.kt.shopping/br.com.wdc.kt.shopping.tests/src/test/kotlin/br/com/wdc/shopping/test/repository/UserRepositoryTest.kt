package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.security.PasswordUtil
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.scripts.sgbd.DBReset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

class UserRepositoryTest : AbstractUserRepositoryTest() {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): UserRepository = env.userRepo

    // -- Só no modo LOCAL: pela API REST a senha nunca é devolvida (ver RestUserRepositoryTest) --

    private val digestProjection get() = User().apply { id = ProjectionValues.i64; password = ProjectionValues.str }

    @Test
    fun passwordDigest_comesOnlyWhenProjected() = runBlocking {
        val stored = repo().fetch(UserCriteria().withUserName("admin").withProjection(digestProjection)).single()
        // é o resumo, não a senha; a coluna é CHAR(32), então vem completado com espaços
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

        val changes = User().apply { id = DBReset.FULANO_ID; userName = "fulano"; name = "Outro Nome"; roles = "CUSTOMER"; password = "nao-deve-gravar" }
        assertTrue(repo().update(changes))

        assertEquals(before, repo().fetchById(DBReset.FULANO_ID, digestProjection)!!.password)
    }

    @Test
    fun update_withThePasswordProjected_writesIt() = runBlocking {
        val changes = User().apply { id = DBReset.FULANO_ID; password = "novo-resumo" }
        assertTrue(repo().update(changes, null, User().apply { password = ProjectionValues.str }))
        assertEquals("novo-resumo", repo().fetchById(DBReset.FULANO_ID, digestProjection)!!.password!!.trim())
    }
}
