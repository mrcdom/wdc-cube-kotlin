package br.com.wdc.framework.domain.repository

import br.com.wdc.framework.domain.pagination.Page

/**
 * Contract of a **read-only** repository: query, count and paginate.
 *
 * It serves two situations:
 *
 * - **data that isn't written through here** — a dashboard view, a report, an aggregate computed in the
 *   database. Whoever exposes it implements only this contract, without having to invent what to do with an
 *   `insert`;
 * - **code that only reads** — a service that declares a dependency on `ReadOnlyRepository` says, through the
 *   type, that it changes nothing, and accepts both a read-only repository and a full one.
 *
 * [Repository] extends this contract with the write operations.
 *
 * @param E entity type (or view row type)
 * @param C search criteria type
 * @param K key type
 */
interface ReadOnlyRepository<E, C, K> {

    suspend fun count(criteria: C): Int

    /**
     * Fetches by the criteria. `0` in [offset] or [limit] means "no skip" / "no limit".
     *
     * Mind the positional order: `fetch(c, 10)` skips 10 rows. To limit, name it: `fetch(c, limit = 10)`.
     */
    suspend fun fetch(criteria: C, offset: Int = 0, limit: Int = 0): List<E>

    suspend fun fetchPage(criteria: C, page: Int, pageSize: Int): Page<E> {
        val total = count(criteria)
        val items = fetch(criteria, page * pageSize, pageSize)
        return Page.of(items, page, pageSize, total)
    }

    /**
     * Fetches by key. Implemented in each entity's interface, as a default that builds the `XxxCriteria` with
     * equality on the key and delegates to [fetch] — so fetching by id is the same query as the others, with the
     * same handling of projection, security and transaction.
     *
     * @param projection `null` projects [ownFieldsProjection]
     * @return the entity, or `null` if there is no row with that key
     */
    suspend fun fetchById(id: K, projection: E? = null): E?

    /**
     * The entity's own fields; associations by key only; no collection nor derived field.
     *
     * It is the default projection: what a query brings when the criteria doesn't say.
     */
    fun ownFieldsProjection(): E
}
