package br.com.wdc.shopping.presentation.presenter.restricted.home.purchases

import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo
import br.com.wdc.framework.cube.ActionOf

class PurchasesPanelScope : Scope() {

    var purchases: List<PurchaseInfo> by field(emptyList())
    var page: Int by field(0)
    var pageSize: Int by field(-1)
    var totalCount: Int by field(0)

    var onOpenReceipt: ActionOf<Long?> = ActionOf.noop()
    var onPageChange: ActionOf<Int?> = ActionOf.noop()
    var onItemSizeCapacityChanged: ActionOf<Int?> = ActionOf.noop()
}
