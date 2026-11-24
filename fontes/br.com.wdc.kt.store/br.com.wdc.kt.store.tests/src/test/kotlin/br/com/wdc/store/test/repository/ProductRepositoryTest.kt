package br.com.wdc.store.test.repository

import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.test.util.TestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
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
