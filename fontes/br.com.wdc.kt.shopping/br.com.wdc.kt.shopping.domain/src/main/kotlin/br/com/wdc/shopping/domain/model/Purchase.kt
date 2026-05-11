package br.com.wdc.shopping.domain.model

import java.time.OffsetDateTime

class Purchase {
    var id: Long? = null
    var buyDate: OffsetDateTime? = null
    var user: User? = null
    var items: MutableList<PurchaseItem>? = null
}
