package br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs

import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.utils.ProjectionValues

class ReceiptItem {

    var id: Long = 0
    var description: String? = null
    var value: Double = 0.0
    var quantity: Int = 0

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
            tgt.value = src.price ?: 0.0
            tgt.quantity = src.amount ?: 0

            if (src.product != null) {
                tgt.description = src.product!!.name
            }

            return tgt
        }
    }
}
