package br.com.wdc.shopping.domain.model

class Purchase {
    var id: Long? = null
    var buyDate: PlatformDateTime? = null
    var user: User? = null
    var items: MutableList<PurchaseItem>? = null
}
