package br.com.wdc.store.presentation.presenter.restricted.receipt.structs

import br.com.wdc.framework.cube.ScopeValue
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.framework.domain.projection.ProjectionValues

class ReceiptItem : ScopeValue {

    var id: Long = 0
    var description: String? = null
    var value: Double = 0.0
    var quantity: Int = 0

    override fun writeJson(json: ExtensibleObjectOutput) {
        json.beginObject()
        json.name("description").value(description)
        json.name("value").value(value)
        json.name("quantity").value(quantity.toLong())
        json.endObject()
    }

    companion object {
        fun projection(): PurchaseItem {
            val pv = ProjectionValues

            val prdPrj = Product()
            prdPrj.name = pv.str

            val prj = PurchaseItem()
            prj.id = pv.i64
            prj.price = pv.f64
            prj.amount = pv.i32
            prj.product = prdPrj
            return prj
        }

        fun create(src: PurchaseItem?): ReceiptItem? {
            if (src == null) return null

            val tgt = ReceiptItem()
            tgt.id = src.id ?: 0
            tgt.value = src.price ?: 0.0
            tgt.quantity = src.amount ?: 0

            if (src.product != null) {
                tgt.description = src.product!!.name
            }

            return tgt
        }
    }
}
