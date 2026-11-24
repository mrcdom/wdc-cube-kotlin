package br.com.wdc.store.test.repository

import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Runs the same tests as [PurchaseRepositoryTest], but over the REST path:
 * HTTP client → in-process Javalin → persistence repositories → H2.
 */
class RestPurchaseRepositoryTest : AbstractPurchaseRepositoryTest() {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-purchase")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): PurchaseRepository = env.purchaseRepo

    override fun purchaseItemRepo(): PurchaseItemRepository = env.purchaseItemRepo
}
