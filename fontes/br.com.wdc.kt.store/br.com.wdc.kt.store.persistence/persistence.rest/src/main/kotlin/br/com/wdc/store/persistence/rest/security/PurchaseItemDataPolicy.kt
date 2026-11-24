package br.com.wdc.store.persistence.rest.security

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.rest.policy.DataPolicy
import br.com.wdc.framework.rest.policy.EntityDescriptor
import br.com.wdc.framework.rest.policy.RowScope
import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.store.domain.security.Role
import br.com.wdc.store.domain.user.User

/**
 * Who may do what with the purchase items through the REST API.
 *
 * - **ADMIN** reads every item, creates, updates and deletes them.
 * - **CUSTOMER** reads the items of its own purchases, and creates items in them — without a price: the price of an
 *   item is the price of its product when it is created, read by the repository before the INSERT.
 * - **MANAGER** reads the items of its own purchases.
 *
 * What a read asks inside `purchase` goes through [PurchaseDataPolicy], and inside `product` through
 * [ProductDataPolicy].
 */
object PurchaseItemDataPolicy {

    private val PV = ProjectionValues

    val PURCHASE_ITEM: EntityDescriptor<PurchaseItem, PurchaseItemCriteria> =
        EntityDescriptor("purchase-item", ::PurchaseItem, ::PurchaseItemCriteria) {
            projection(PurchaseItemCriteria::projection, PurchaseItemCriteria::withProjection)
            anyOf(PurchaseItemCriteria::anyOf, PurchaseItemCriteria::withAnyOf)
            key(PurchaseItem::id, PV.i64)
            value(PurchaseItem::amount, PV.i32)
            value(PurchaseItem::price, PV.f64)
            relation(PurchaseItem::purchase) { PurchaseDataPolicy.POLICY }
            relation(PurchaseItem::product) { ProductDataPolicy.POLICY }
            criterion("purchaseItemId", PurchaseItem::id)
            criterion("purchaseId", PurchaseItem::purchase)
            criterion("productId", PurchaseItem::product)
            criterion("userId", PurchaseItem::purchase)
            orderings { c ->
                when (c.orderBy) {
                    null, PurchaseItemCriteria.OrderBy.OLDEST_FIRST, PurchaseItemCriteria.OrderBy.NEWEST_FIRST -> emptySet()
                    PurchaseItemCriteria.OrderBy.MOST_EXPENSIVE_FIRST -> setOf(PurchaseItem::price)
                    PurchaseItemCriteria.OrderBy.LARGEST_QUANTITY_FIRST -> setOf(PurchaseItem::amount)
                }
            }
            defaultShape { PurchaseItemDataPolicy.defaultShape() }
        }

    /** A read without a projection: quantity, price and the product's name and price — also a receipt's line. */
    fun defaultShape(): PurchaseItem = PurchaseItem().apply {
        amount = PV.i32
        price = PV.f64
        product = Product().apply { name = PV.str; price = PV.f64 }
    }

    /**
     * The items of the requester's own purchases.
     *
     * A read fetches `purchase.user` for the check. A new item may come with its purchase by key only — sent on its
     * own to `purchase-item/insert` —, and then the owner of that purchase is looked up (inside the write's transaction:
     * it sees a purchase opened in it and not yet committed).
     */
    object OWN : RowScope<PurchaseItem, PurchaseItemCriteria> {
        override fun restrict(group: PurchaseItemCriteria, requester: RestPrincipal) {
            group.userId.eq(requester.userId())
        }

        override suspend fun covers(row: PurchaseItem, requester: RestPrincipal): Boolean {
            val purchase = row.purchase ?: return false
            var owner = purchase.user?.id
            if (owner == null && purchase.user == null && purchase.id != null) {
                owner = PurchaseRepository.BEAN.get()
                    .fetchById(purchase.id!!, Purchase().apply { id = PV.i64; user = User().apply { id = PV.i64 } })
                    ?.user?.id
            }
            return owner != null && owner == requester.userId()
        }

        override fun projection(): PurchaseItem =
            PurchaseItem().apply { purchase = Purchase().apply { user = User().apply { id = PV.i64 } } }

        override fun toString(): String = "OWN"
    }

    val POLICY: DataPolicy<PurchaseItem, PurchaseItemCriteria> = DataPolicy(PURCHASE_ITEM) {
        grant(Role.ADMIN.name) {
            read(PurchaseItem::amount, PurchaseItem::price, PurchaseItem::purchase, PurchaseItem::product)
            create(PurchaseItem::amount, PurchaseItem::price, PurchaseItem::purchase, PurchaseItem::product)
            update(PurchaseItem::amount, PurchaseItem::price, PurchaseItem::purchase, PurchaseItem::product)
            delete()
        }
        grant(Role.CUSTOMER.name) {
            rows(OWN)
            read(PurchaseItem::amount, PurchaseItem::price, PurchaseItem::purchase, PurchaseItem::product)
            create(PurchaseItem::amount, PurchaseItem::purchase, PurchaseItem::product)
        }
        grant(Role.MANAGER.name) {
            rows(OWN)
            read(PurchaseItem::amount, PurchaseItem::price, PurchaseItem::purchase, PurchaseItem::product)
        }
    }
}
