package br.com.wdc.shopping.presentation.presenter.restricted.home.purchases

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractChildPresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import kotlin.math.ceil
import kotlin.math.max

class PurchasesPanelPresenter(
    app: ShoppingApplication,
    val owner: HomePresenter,
) : AbstractChildPresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger("PurchasesPanelPresenter")

        var createView: ((PurchasesPanelPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = observed(PurchasesPanelViewState())

    // :: Internal Instance Fields

    private val purchasesPanelService = PurchasesPanelService(app.getPurchaseRepository())

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
        if (requested == state.page) return
        state.page = requested
        if (state.pageSize > 0) loadPurchases()
    }

    suspend fun onPageChange(page: Int) {
        state.page = max(0, page)
        loadPurchases()
        // not a navigation: the address follows the page here
        app.updateHistory()
    }

    suspend fun onItemSizeCapacityChanged(capacity: Int) {
        val newPageSize = max(1, capacity)
        if (newPageSize != state.pageSize) {
            // the first capacity keeps the page the address asked for; a later change goes back to the first page
            if (state.pageSize > 0) state.page = 0
            state.pageSize = newPageSize
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
        if (subject != null && state.pageSize > 0) {
            var page = purchasesPanelService.fetchPageOfUser(subject.id!!, state.page, state.pageSize)

            state.totalCount = page.totalItems

            val totalPages = max(1, ceil(state.totalCount.toDouble() / state.pageSize).toInt())
            if (state.page >= totalPages) {
                // the requested page is past the end (the view capacity changed, or purchases disappeared): show the
                // last one, with its items — the result above is the one of the page that does not exist
                state.page = totalPages - 1
                page = purchasesPanelService.fetchPageOfUser(subject.id!!, state.page, state.pageSize)
                state.totalCount = page.totalItems
            }

            state.purchases = page.items
        }
    }
}
