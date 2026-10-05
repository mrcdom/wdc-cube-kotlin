package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

class PurchaseItemRepositoryTest : AbstractPurchaseItemRepositoryTest() {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): PurchaseItemRepository = env.purchaseItemRepo
}
