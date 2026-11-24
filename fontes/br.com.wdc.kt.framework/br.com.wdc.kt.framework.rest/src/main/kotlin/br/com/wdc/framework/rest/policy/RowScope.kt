package br.com.wdc.framework.rest.policy

import br.com.wdc.framework.rest.security.RestPrincipal

/**
 * Which rows of an entity a [DataGrant] reaches — all of them, or those related to the requester (their own user,
 * their orders).
 *
 * It is said twice, in two languages, and both must agree: [restrict] as criteria, so the database returns only the
 * reachable rows; [covers] in memory, so each returned row gets the fields of the grants that reach it. A user with two
 * roles may reach a row through one and not the other, and the fields of that row are the ones of the grants that reach
 * it — not the union of everything the user may read anywhere. On a write, [covers] runs on the row being created, and
 * on the stored row loaded for an update.
 *
 * A policy compares scopes by identity: declare each one once — an `object` — and share it among the grants.
 *
 * ```
 * object OwnOrders : RowScope<Order, OrderCriteria> {
 *     override fun restrict(group: OrderCriteria, requester: RestPrincipal) { group.customerId.eq(requester.id.toLong()) }
 *     override suspend fun covers(row: Order, requester: RestPrincipal) = row.customer?.id == requester.id.toLong()
 *     override fun projection() = Order().apply { customer = Customer().apply { id = ProjectionValues.i64 } }
 * }
 * ```
 *
 * @param E the entity
 * @param C its criteria
 */
interface RowScope<E, C> {

    /** Sets on [group] — an empty criteria — the fields that select the reachable rows. */
    fun restrict(group: C, requester: RestPrincipal)

    /**
     * Whether [row] is reachable. It reads only what [projection] names, which is fetched for that; it may look up
     * what the row does not carry (the owner of the order an item points to) — it runs inside the request, and inside
     * the write's transaction on a write.
     */
    suspend fun covers(row: E, requester: RestPrincipal): Boolean

    /**
     * A projection of what [covers] reads beyond the key — it may go into relations, as the items of one's own orders
     * read `order.customer`. `null` when it reads only the key. A new projection on each call.
     */
    fun projection(): E? = null

    companion object {
        /** Every row. */
        @Suppress("UNCHECKED_CAST")
        fun <E, C> all(): RowScope<E, C> = AllRows as RowScope<E, C>
    }
}

/** [RowScope.all]: nothing to restrict, every row covered. */
internal object AllRows : RowScope<Any?, Any?> {
    override fun restrict(group: Any?, requester: RestPrincipal) = Unit
    override suspend fun covers(row: Any?, requester: RestPrincipal): Boolean = true
    override fun toString(): String = "ALL"
}
