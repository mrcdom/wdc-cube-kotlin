package br.com.wdc.shopping.presentation.presenter.restricted.home.structs

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.utils.ProjectionValues

class PurchaseInfo {

    var id: Long = 0
    var date: Long = 0
    var total: Double = 0.0
    var items: List<String> = emptyList()

    companion object {
        fun projectionWithItens(): Purchase {
            val pv = ProjectionValues

            val prdPrj = Product()
            prdPrj.name = pv.str

            val itemPrj = PurchaseItem()
            itemPrj.price = pv.f64
            itemPrj.amount = pv.i32
            itemPrj.product = prdPrj

            val prj = Purchase()
            prj.id = pv.i64
            prj.buyDate = pv.offsetDateTime
            prj.items = mutableListOf(itemPrj)

            return prj
        }

        fun create(src: Purchase?): PurchaseInfo? {
            if (src == null) return null

            val pv = ProjectionValues

            val tgt = PurchaseInfo()
            tgt.id = src.id ?: -1L

            val buyDate = CoerceUtils.asDate(src.buyDate)
            tgt.date = (buyDate ?: pv.date).time
            val itemsList = mutableListOf<String>()

            var total = 0.0
            for (item in src.items ?: emptyList()) {
                val price = item.price ?: 0.0
                val amount = item.amount ?: 0
                total += price * amount

                if (item.product != null && !item.product!!.name.isNullOrBlank()) {
                    itemsList.add(item.product!!.name!!)
                }
            }

            tgt.total = total
            tgt.items = itemsList

            return tgt
        }
    }
}
