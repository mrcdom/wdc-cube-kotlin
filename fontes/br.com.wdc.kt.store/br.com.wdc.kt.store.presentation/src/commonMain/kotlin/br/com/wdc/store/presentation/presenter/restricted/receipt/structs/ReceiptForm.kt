package br.com.wdc.store.presentation.presenter.restricted.receipt.structs

import br.com.wdc.framework.cube.ScopeValue
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.framework.domain.projection.ProjectionValues

class ReceiptForm : ScopeValue {

    var date: Long? = null
    var items: MutableList<ReceiptItem> = mutableListOf()
    var total: Double? = null

    override fun writeJson(json: ExtensibleObjectOutput) {
        json.beginObject()
        date?.let { json.name("date").value(it) }
        total?.let { json.name("total").value(it) }
        json.name("items").beginArray()
        for (item in items) item.writeJson(json)
        json.endArray()
        json.endObject()
    }

    companion object {
        fun projection(): Purchase {
            val pv = ProjectionValues

            val prj = Purchase()
            prj.buyDate = pv.instant
            prj.items = mutableListOf(ReceiptItem.projection())

            return prj
        }

        fun create(src: Purchase?): ReceiptForm? {
            if (src == null) return null

            val tgt = ReceiptForm()

            tgt.date = src.buyDate?.toEpochMilliseconds()
            tgt.items = mutableListOf()

            var total = 0.0
            if (src.items != null) {
                for (purchaseItem in src.items!!) {
                    if (purchaseItem.price != null) {
                        val amount = purchaseItem.amount ?: 0
                        total += purchaseItem.price!! * amount
                    }

                    ReceiptItem.create(purchaseItem)?.let { tgt.items.add(it) }
                }
            }
            tgt.total = total

            return tgt
        }
    }
}
