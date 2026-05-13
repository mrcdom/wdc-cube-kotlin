package br.com.wdc.shopping.test.service

import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ShoppingServiceTest {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @Test
    fun test1() = ShoppingServiceTestAlgorithm.testPurchaseItemFetch(env)

    @Test
    fun test() = ShoppingServiceTestAlgorithm.testFullShoppingWorkflow(env)
}
