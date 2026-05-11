package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.repositories.UserRepository
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
}
