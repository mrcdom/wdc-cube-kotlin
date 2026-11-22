package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Runs the same tests as [PurchaseItemRepositoryTest], but over the REST path:
 * HTTP client → in-process Javalin → persistence repositories → H2.
 */
class RestPurchaseItemRepositoryTest : AbstractPurchaseItemRepositoryTest() {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-purchase-item")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): PurchaseItemRepository = env.purchaseItemRepo
}
