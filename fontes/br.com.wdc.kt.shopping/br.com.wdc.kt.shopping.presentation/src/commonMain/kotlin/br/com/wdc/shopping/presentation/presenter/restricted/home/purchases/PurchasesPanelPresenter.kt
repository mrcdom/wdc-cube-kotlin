package br.com.wdc.shopping.presentation.presenter.restricted.home.purchases

import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractChildPresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import kotlin.math.ceil
import kotlin.math.max

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

    // :: Life cycle

    override fun onCreateView(): CubeView = createView!!.invoke(this)

    override suspend fun onInitialize() {
        // NOOP: a new view paints the current state; the page comes with onPageChange
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

    suspend fun onPageChange(page: Int) {
        scope.page = max(0, page)
        loadPurchases()
        // not a navigation: the address follows the page here
        app.updateHistory()
    }

    suspend fun onItemSizeCapacityChanged(capacity: Int) {
        val newPageSize = max(1, capacity)
        if (newPageSize != scope.pageSize) {
            // the first capacity keeps the page the address asked for; a later change goes back to the first page
            if (scope.pageSize > 0) scope.page = 0
            scope.pageSize = newPageSize
            loadPurchases()
            app.updateHistory()
        }
    }

    suspend fun onOpenReceipt(purchaseId: Long?) {
        owner.onOpenReceipt(purchaseId)
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
