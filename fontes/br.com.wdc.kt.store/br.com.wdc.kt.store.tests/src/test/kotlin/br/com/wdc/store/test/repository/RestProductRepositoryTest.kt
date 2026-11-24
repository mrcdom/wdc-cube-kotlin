package br.com.wdc.store.test.repository

import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Runs the same tests as [ProductRepositoryTest], but over the REST path:
 * HTTP client → in-process Javalin → persistence repositories → H2.
 */
class RestProductRepositoryTest : AbstractProductRepositoryTest() {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-product")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): ProductRepository = env.productRepo
}
