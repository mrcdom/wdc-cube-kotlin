package br.com.wdc.store.test.service

import br.com.wdc.store.test.util.TestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class StoreServiceTest {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @Test
    fun test1() = runBlocking { StoreServiceTestAlgorithm.testPurchaseItemFetch(env) }

    @Test
    fun test() = runBlocking { StoreServiceTestAlgorithm.testFullShoppingWorkflow(env) }
}
