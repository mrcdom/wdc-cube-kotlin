package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Executa os mesmos testes de [ProductRepositoryTest], porém pelo caminho REST:
 * client HTTP → Javalin in-process → repositórios de persistência → H2.
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
