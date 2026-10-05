package br.com.wdc.shopping.presentation.presenter.restricted.home.purchases

import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.framework.domain.pagination.Page
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo

class PurchasesPanelService(private val repo: PurchaseRepository) {

    suspend fun loadPurchases(criteria: PurchaseCriteria): List<PurchaseInfo> {
        return repo.fetch(criteria.withProjection(PurchaseInfo.projectionWithItens()))
            .mapNotNull { PurchaseInfo.create(it) }
    }

    suspend fun countPurchasesOfUser(userId: Long): Int {
        return repo.count(PurchaseCriteria().withUserId(userId))
    }

    suspend fun loadPurchasesOfUser(userId: Long): List<PurchaseInfo> {
        return loadPurchasesOfUser(userId, null, null)
    }

    suspend fun loadPurchasesOfUser(userId: Long, offset: Int?, limit: Int?): List<PurchaseInfo> {
        return repo.fetch(statementOf(userId), offset ?: 0, limit ?: 0).mapNotNull { PurchaseInfo.create(it) }
    }

    /** Uma página do extrato do usuário; `totalItems` é o total de compras, e não o da página. */
    suspend fun fetchPageOfUser(userId: Long, page: Int, pageSize: Int): Page<PurchaseInfo> {
        val result = repo.fetchPage(statementOf(userId), page, pageSize)
        return Page.of(result.items.mapNotNull { PurchaseInfo.create(it) }, page, pageSize, result.totalItems)
    }

    /** O extrato: as compras do usuário, da mais recente para a mais antiga. */
    private fun statementOf(userId: Long): PurchaseCriteria =
        PurchaseCriteria()
            .withUserId(userId)
            .withProjection(PurchaseInfo.projectionWithItens())
            .withOrderBy(PurchaseCriteria.OrderBy.MOST_RECENT_PURCHASE_FIRST)
}
