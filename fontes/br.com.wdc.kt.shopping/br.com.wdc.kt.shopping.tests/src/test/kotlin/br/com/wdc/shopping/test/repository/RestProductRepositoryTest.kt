package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Runs the same tests as [ProductRepositoryTest], but over the REST path:
 * HTTP client → in-process Javalin → persistence repositories → H2.
 */
class RestProductRepositoryTest : AbstractProductRepositoryTest() {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-product")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): ProductRepository = env.productRepo
}
