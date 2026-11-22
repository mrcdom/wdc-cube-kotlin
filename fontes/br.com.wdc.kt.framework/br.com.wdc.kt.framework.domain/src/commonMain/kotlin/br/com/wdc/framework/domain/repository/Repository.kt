package br.com.wdc.framework.domain.repository

/**
 * Contract of the repositories that read **and write**: the queries of [ReadOnlyRepository] plus insert,
 * update and delete.
 *
 * It holds the same for the implementation that talks to the database (server) and for the one that talks HTTP
 * (client): this is what lets the same use case run on both sides.
 *
 * @param E entity type
 * @param C search criteria type
 * @param K primary key type
 */
interface Repository<E, C, K> : ReadOnlyRepository<E, C, K> {

    /**
     * Inserts [bean]. The [projection] says which fields go into the INSERT (non-null field in it); what is left out
     * gets the column default. Without a projection, the fields that have a value in the bean go in (null is left
     * out and also gets the default). Returns `true` if the row went in, and [bean] itself comes out complete: the
     * generated key and what the database filled in (defaults, computed columns).
     *
     * The same holds for [update], which already takes the projection. The difference is that, in insert, being
     * left out means "column default", not "unchanged".
     */
    suspend fun insert(bean: E, projection: E? = null): Boolean

    /**
     * Updates an entity.
     *
     * @param newBean    new values
     * @param oldBean    old values, basis for the comparison (may be `null`)
     * @param projection says which fields to consider — non-null field in the projection = "update this field",
     *                   **even with `null`, which clears the column**. `null`: with [oldBean], the fields that
     *                   changed; without it, only the fields with a value in [newBean], as in [insert] (a null
     *                   clears nothing). To clear a column, mark it in a projection.
     */
    suspend fun update(newBean: E, oldBean: E? = null, projection: E? = null): Boolean

    /** Inserts if [oldBean] is `null`; updates otherwise. The [projection] applies to both. */
    suspend fun insertOrUpdate(newBean: E, oldBean: E?, projection: E? = null): Boolean =
        if (oldBean == null) insert(newBean, projection) else update(newBean, oldBean, projection)

    suspend fun delete(criteria: C): Int
}
