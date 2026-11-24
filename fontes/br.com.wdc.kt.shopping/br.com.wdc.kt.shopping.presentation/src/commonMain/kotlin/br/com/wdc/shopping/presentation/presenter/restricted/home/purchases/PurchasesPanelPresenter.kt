package br.com.wdc.shopping.presentation.presenter.restricted.home.purchases

import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractChildPresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import kotlin.math.ceil
import kotlin.math.max
import br.com.wdc.framework.cube.FormParam

class PurchasesPanelPresenter(
    app: RootPresenter,
    val owner: HomePresenter,
) : AbstractChildPresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("PurchasesPanelPresenter")

        var createView: ((PurchasesPanelPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val scope = bind(PurchasesPanelScope())

    // :: Internal Instance Fields

    private val purchasesPanelService = PurchasesPanelService(PurchaseRepository.BEAN.get())

    init {
        // not navigations: the guard republishes the address, which follows the page
        scope.onOpenReceipt = actionOf(1, FormParam.long("p.purchaseId")) { owner.openReceipt(it) }
        scope.onPageChange = actionOf(2, FormParam.int("p.page")) { changePage(it ?: 0) }
        scope.onItemSizeCapacityChanged = actionOf(3, FormParam.int("p.capacity")) { changeCapacity(it ?: 0) }
    }

    // :: Life cycle

    override fun onCreateView(): CubeView = createView!!.invoke(this)

    override suspend fun onInitialize() {
        // NOOP: a new view paints the current state; the page comes with the view's first capacity
    }

    // :: User Actions

    /**
     * The page the address asks for (the home reads it). Loaded now if it changed and the view already told its
     * capacity; otherwise with the first capacity.
     */
    suspend fun restorePage(page: Int) {
        val requested = max(0, page)
        if (requested == scope.page) return
        scope.page = requested
        if (scope.pageSize > 0) loadPurchases()
    }

    /** Shows [page] of the purchases (the home also calls it after a checkout). */
    suspend fun changePage(page: Int) {
        scope.page = max(0, page)
        loadPurchases()
    }

    private suspend fun changeCapacity(capacity: Int) {
        val newPageSize = max(1, capacity)
        if (newPageSize != scope.pageSize) {
            // the first capacity keeps the page the address asked for; a later change goes back to the first page
            if (scope.pageSize > 0) scope.page = 0
            scope.pageSize = newPageSize
            loadPurchases()
        }
    }

    // :: Data load

    suspend fun loadPurchases() {
        val subject = app.subject
        if (subject != null && scope.pageSize > 0) {
            var page = purchasesPanelService.fetchPageOfUser(subject.id!!, scope.page, scope.pageSize)

            scope.totalCount = page.totalItems

            val totalPages = max(1, ceil(scope.totalCount.toDouble() / scope.pageSize).toInt())
            if (scope.page >= totalPages) {
                // the requested page is past the end (the view capacity changed, or purchases disappeared): show the
                // last one, with its items — the result above is the one of the page that does not exist
                scope.page = totalPages - 1
                page = purchasesPanelService.fetchPageOfUser(subject.id!!, scope.page, scope.pageSize)
                scope.totalCount = page.totalItems
            }

            scope.purchases = page.items
        }
    }
}
