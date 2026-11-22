package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Runs the same tests as [PurchaseRepositoryTest], but over the REST path:
 * HTTP client → in-process Javalin → persistence repositories → H2.
 */
class RestPurchaseRepositoryTest : AbstractPurchaseRepositoryTest() {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-purchase")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): PurchaseRepository = env.purchaseRepo

    override fun purchaseItemRepo(): PurchaseItemRepository = env.purchaseItemRepo
}
