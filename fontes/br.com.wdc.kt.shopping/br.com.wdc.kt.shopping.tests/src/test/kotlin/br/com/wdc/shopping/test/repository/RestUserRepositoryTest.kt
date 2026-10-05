package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.repositories.UserRepository
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
}
