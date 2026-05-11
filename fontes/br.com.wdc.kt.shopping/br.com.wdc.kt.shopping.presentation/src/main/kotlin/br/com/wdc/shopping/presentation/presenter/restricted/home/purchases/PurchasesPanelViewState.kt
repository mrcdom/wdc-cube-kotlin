package br.com.wdc.shopping.presentation.presenter.restricted.home.purchases

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.ViewState
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo

class PurchasesPanelViewState : ViewState {

    var purchases: List<PurchaseInfo> = emptyList()
    var page: Int = 0
    var pageSize: Int = -1
    var totalCount: Int = 0

    override fun write(instanceId: String, json: ExtensibleObjectOutput) {
        json.beginObject()

        json.name("id").value(instanceId)
        json.name("page").value(page.toLong())
        json.name("pageSize").value(pageSize.toLong())
        json.name("totalCount").value(totalCount.toLong())

        json.name("purchases").beginArray()
        for (purchase in purchases) {
            json.beginObject()
            json.name("id").value(purchase.id)
            json.name("date").value(purchase.date)
            json.name("total").value(purchase.total)
            json.name("items").beginArray()
            for (item in purchase.items) {
                json.value(item)
            }
            json.endArray()
            json.endObject()
        }
        json.endArray()

        json.endObject()
    }
}
