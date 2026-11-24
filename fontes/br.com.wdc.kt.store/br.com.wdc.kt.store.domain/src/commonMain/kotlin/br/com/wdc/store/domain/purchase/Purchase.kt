package br.com.wdc.store.domain.purchase

import br.com.wdc.framework.domain.codec.KeyedEntity
import br.com.wdc.store.domain.model.PlatformDateTime
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.user.User

/**
 * A user's purchase, with its items.
 *
 * The fields are nullable because `null` has two roles: missing data and, in a projection, "do not fetch this field".
 * In [items], the projection is a collection with **one** element — the item's shape — and may carry criteria,
 * ordering and slicing (`ProjectionValues.singletonList`).
 */
class Purchase : KeyedEntity {
    var id: Long? = null
    var buyDate: PlatformDateTime? = null
    var user: User? = null
    var items: MutableList<PurchaseItem>? = null

    val userId: Long? get() = user?.id

    override fun key(): Any? = id
}
