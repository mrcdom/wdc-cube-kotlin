package br.com.wdc.store.presentation.presenter.restricted.home.calls

import br.com.wdc.framework.domain.pagination.Page
import br.com.wdc.framework.domain.resource.ResourceQuery
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.presentation.presenter.restricted.home.structs.PurchaseInfo

/**
 * One page of the user's statement, from the most recent purchase to the oldest; `totalItems` is the total of
 * purchases, not of the page.
 */
class PurchasesPageQuery(private val userId: Long, private val page: Int, private val pageSize: Int) :
    ResourceQuery<Page<PurchaseInfo>>() {

    private val purchaseRepo: PurchaseRepository get() = PurchaseRepository.BEAN.get()

    override suspend fun run(): Page<PurchaseInfo> {
        val criteria = PurchaseCriteria()
            .withUserId(userId)
            .withProjection(PurchaseInfo.projectionWithItems())
            .withOrderBy(PurchaseCriteria.OrderBy.MOST_RECENT_PURCHASE_FIRST)
        val result = purchaseRepo.fetchPage(criteria, page, pageSize)
        return Page.of(result.items.mapNotNull { PurchaseInfo.create(it) }, page, pageSize, result.totalItems)
    }
}
