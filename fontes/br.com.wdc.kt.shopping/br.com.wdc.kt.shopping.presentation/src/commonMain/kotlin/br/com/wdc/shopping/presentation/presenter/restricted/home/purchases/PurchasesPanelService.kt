package br.com.wdc.shopping.presentation.presenter.restricted.home.purchases

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
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
        return repo.fetch(
            PurchaseCriteria()
                .withUserId(userId)
                .withProjection(PurchaseInfo.projectionWithItens())
                .withOrderBy(PurchaseCriteria.OrderBy.DESCENDING)
                .withOffset(offset)
                .withLimit(limit)
        ).mapNotNull { PurchaseInfo.create(it) }
    }

    suspend fun fetchPageOfUser(userId: Long, offset: Int?, limit: Int?): Page<PurchaseInfo> {
        val page = repo.fetchPage(
            PurchaseCriteria()
                .withUserId(userId)
                .withProjection(PurchaseInfo.projectionWithItens())
                .withOrderBy(PurchaseCriteria.OrderBy.DESCENDING)
                .withOffset(offset)
                .withLimit(limit)
        )
        return Page(page.items.mapNotNull { PurchaseInfo.create(it) }, page.totalCount)
    }
}
