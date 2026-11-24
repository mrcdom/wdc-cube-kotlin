package br.com.wdc.store.test.service

import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Runs the same tests as [StoreServiceTest], but using
 * [RestTestEnvironment] — the repositories are REST client implementations
 * that communicate over HTTP with an in-process Javalin.
 */
class StoreServiceRestTest {

    companion object {
        private val env = RestTestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @Test
    fun test1() = runBlocking { StoreServiceTestAlgorithm.testPurchaseItemFetch(env) }

    @Test
    fun test() = runBlocking { StoreServiceTestAlgorithm.testFullShoppingWorkflow(env) }
}
