package br.com.wdc.shopping.presentation.presenter.restricted.home.purchases

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo

class PurchasesPanelService {

    private val repo: PurchaseRepository

    constructor(app: ShoppingApplication) {
        this.repo = app.getPurchaseRepository()
    }

    constructor(repo: PurchaseRepository) {
        this.repo = repo
    }

    fun loadPurchases(criteria: PurchaseCriteria): List<PurchaseInfo> {
        return repo.fetch(criteria.withProjection(PurchaseInfo.projectionWithItens()))
            .mapNotNull { PurchaseInfo.create(it) }
    }

    fun countPurchasesOfUser(userId: Long): Int {
        return repo.count(PurchaseCriteria().withUserId(userId))
    }

    fun loadPurchasesOfUser(userId: Long): List<PurchaseInfo> {
        return loadPurchasesOfUser(userId, null, null)
    }

    fun loadPurchasesOfUser(userId: Long, offset: Int?, limit: Int?): List<PurchaseInfo> {
        return repo.fetch(
            PurchaseCriteria()
                .withUserId(userId)
                .withProjection(PurchaseInfo.projectionWithItens())
                .withOrderBy(PurchaseCriteria.OrderBy.DESCENDING)
                .withOffset(offset)
                .withLimit(limit)
        ).mapNotNull { PurchaseInfo.create(it) }
    }
}
