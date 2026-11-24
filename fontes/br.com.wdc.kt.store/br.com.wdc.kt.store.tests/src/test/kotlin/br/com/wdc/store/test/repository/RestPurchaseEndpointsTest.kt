package br.com.wdc.store.test.repository

import br.com.wdc.store.domain.exception.BusinessException
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** The purchase HTTP contract seen from outside, without going through the client repository. */
class RestPurchaseEndpointsTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-purchase-endpoints")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun post(operation: String, body: String) = env.transport.postJson("/api/repo/purchase/$operation", body)

    @Test
    fun projectedItems_withNegativeSlice_areRefused() {
        // the database refused the negative LIMIT/OFFSET, and the API answered 500
        for (slice in listOf(""""limit":-1""", """"offset":-1""")) {
            val body = """{"projection":{"id":1,"items":{"shape":{"id":1},$slice}}}"""
            val e = assertThrows(BusinessException::class.java, { post("fetch", body) }, slice)
            assertTrue(e.message!!.contains("negative"), e.message)
        }
    }

    @Test
    fun projectedItems_withASlice_stillWork() {
        val response = post("fetch", """{"projection":{"id":1,"items":{"shape":{"id":1},"limit":1,"offset":0}}}""")
        assertTrue(response.startsWith("""{"items":["""), response)
    }
}
