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

    val state = PurchasesPanelViewState()

    // :: Internal Instance Fields

    private val purchasesPanelService = PurchasesPanelService(app.getPurchaseRepository())

    // :: Life cycle

    override fun onCreateView(): CubeView = createView!!.invoke(this)

    override suspend fun onInitialize() {
        update()
    }

    // :: User Actions

    suspend fun onPageChange(page: Int) {
        state.page = max(0, page)
        loadPurchases()
    }

    suspend fun onItemSizeCapacityChanged(capacity: Int) {
        val newPageSize = max(1, capacity)
        if (newPageSize != state.pageSize) {
            state.pageSize = newPageSize
            state.page = 0
            loadPurchases()
        }
    }

    suspend fun onOpenReceipt(purchaseId: Long?) {
        owner.onOpenReceipt(purchaseId)
    }

    // :: Data load

    suspend fun loadPurchases() {
        val subject = app.subject
        if (subject != null && state.pageSize > 0) {
            val offset = state.page * state.pageSize
            val page = purchasesPanelService.fetchPageOfUser(subject.id!!, offset, state.pageSize)

            state.totalCount = page.totalCount

            val totalPages = max(1, ceil(state.totalCount.toDouble() / state.pageSize).toInt())
            if (state.page >= totalPages) {
                state.page = totalPages - 1
            }

            state.purchases = page.items
            update()
        }
    }
}
