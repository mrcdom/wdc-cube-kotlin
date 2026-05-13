package br.com.wdc.shopping.test.service

import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Executa os mesmos testes de [ShoppingServiceTest], porém usando
 * [RestTestEnvironment] — os repositórios são implementações REST client
 * que comunicam via HTTP com um Javalin in-process.
 */
class ShoppingServiceRestTest {

    companion object {
        private val env = RestTestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @Test
    fun test1() = ShoppingServiceTestAlgorithm.testPurchaseItemFetch(env)

    @Test
    fun test() = ShoppingServiceTestAlgorithm.testFullShoppingWorkflow(env)
}
