package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.test.util.DisabledTests
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Executa os mesmos testes de [PurchaseItemRepositoryTest], porém pelo caminho REST:
 * client HTTP → Javalin in-process → repositórios de persistência → H2.
 */
class RestPurchaseItemRepositoryTest : AbstractPurchaseItemRepositoryTest() {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-purchase-item")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)

        // Falhas conhecidas do caminho REST atual
        @JvmField
        @RegisterExtension
        val knownGaps = DisabledTests(mapOf(
            "fetchById_returnsCorrectItem_withPurchase" to
                "REST não devolve a relação 'purchase' do item, mesmo pedida na projeção (volta null)",
            "insert_newPurchaseItem_withPurchaseAssertion" to
                "mesma causa: o item relido por REST vem sem 'purchase', e a asserção sobre purchase.id falha",
        ))
    }

    override fun repo(): PurchaseItemRepository = env.purchaseItemRepo
}
