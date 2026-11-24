package br.com.wdc.shopping.persistence.rest.security

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.rest.policy.DataPolicy
import br.com.wdc.framework.rest.policy.EntityDescriptor
import br.com.wdc.framework.rest.policy.RowScope
import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.security.Role
import br.com.wdc.shopping.domain.user.User

/**
 * Who may do what with the purchases through the REST API. **A purchase is a receipt**: once created, only an ADMIN
 * changes it.
 *
 * - **ADMIN** reads every purchase, creates, updates and deletes them.
 * - **CUSTOMER** reads and creates its own purchases — a purchase on someone else's behalf is refused.
 * - **MANAGER** reads its own purchases.
 *
 * What a read asks inside `user` goes through [UserDataPolicy], and inside `items` through [PurchaseItemDataPolicy];
 * the items of a new purchase are created through the latter, as items of that purchase.
 */
object PurchaseDataPolicy {

    private val PV = ProjectionValues

    val PURCHASE: EntityDescriptor<Purchase, PurchaseCriteria> = EntityDescriptor("purchase", ::Purchase, ::PurchaseCriteria) {
        projection(PurchaseCriteria::projection, PurchaseCriteria::withProjection)
        anyOf(PurchaseCriteria::anyOf, PurchaseCriteria::withAnyOf)
        key(Purchase::id, PV.i64)
        value(Purchase::buyDate, PV.instant)
        relation(Purchase::user) { UserDataPolicy.POLICY }
        collection(Purchase::items, inverse = PurchaseItem::purchase) { PurchaseItemDataPolicy.POLICY }
        criterion("purchaseId", Purchase::id)
        criterion("userId", Purchase::user)
        criterion("productId", Purchase::items)
        orderings { c ->
            when (c.orderBy) {
                null, PurchaseCriteria.OrderBy.OLDEST_FIRST, PurchaseCriteria.OrderBy.NEWEST_FIRST -> emptySet()
                PurchaseCriteria.OrderBy.MOST_RECENT_PURCHASE_FIRST, PurchaseCriteria.OrderBy.EARLIEST_PURCHASE_FIRST -> setOf(Purchase::buyDate)
            }
        }
        // a read without a projection: the receipt — date, buyer's name, and the items with their products
        defaultShape {
            Purchase().apply {
                buyDate = PV.instant
                user = User().apply { name = PV.str }
                items = PV.singletonList(PurchaseItemDataPolicy.defaultShape(), PurchaseItemCriteria())
            }
        }
    }

    /** The requester's own purchases. */
    object OWN : RowScope<Purchase, PurchaseCriteria> {
        override fun restrict(group: PurchaseCriteria, requester: RestPrincipal) {
            group.userId.eq(requester.userId())
        }

        override suspend fun covers(row: Purchase, requester: RestPrincipal): Boolean =
            row.user?.id != null && row.user?.id == requester.userId()

        override fun projection(): Purchase = Purchase().apply { user = User().apply { id = PV.i64 } }

        override fun toString(): String = "OWN"
    }

    val POLICY: DataPolicy<Purchase, PurchaseCriteria> = DataPolicy(PURCHASE) {
        grant(Role.ADMIN.name) {
            read(Purchase::buyDate, Purchase::user, Purchase::items)
            create(Purchase::buyDate, Purchase::user, Purchase::items)
            update(Purchase::buyDate, Purchase::user)
            delete()
        }
        grant(Role.CUSTOMER.name) {
            rows(OWN)
            read(Purchase::buyDate, Purchase::user, Purchase::items)
            create(Purchase::buyDate, Purchase::user, Purchase::items)
        }
        grant(Role.MANAGER.name) {
            rows(OWN)
            read(Purchase::buyDate, Purchase::user, Purchase::items)
        }
    }
}
