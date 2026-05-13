package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

class ProductRepositoryTest : AbstractProductRepositoryTest() {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): ProductRepository = env.productRepo
}
