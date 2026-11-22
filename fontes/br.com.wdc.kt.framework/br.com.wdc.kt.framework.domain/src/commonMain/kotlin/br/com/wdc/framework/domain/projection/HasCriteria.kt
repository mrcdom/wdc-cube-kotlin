package br.com.wdc.framework.domain.projection

/**
 * Projection collection that carries an additional filtering criteria, propagated to the relation's query.
 */
interface HasCriteria {
    val criteria: Any?
}

/**
 * Projection collection that slices how many child rows to bring.
 *
 * **Slicing without ordering returns rows in undefined order** — the database promises no order at all without
 * `ORDER BY`. The order comes from the criteria the collection carries, via [HasCriteria]: that is where the
 * child entity's `OrderBy` lives.
 */
interface HasSlice {
    /** Maximum child rows, or `null` to bring all. */
    val limit: Int?

    /** How many rows to skip before starting, or `null` to start from the first. */
    val offset: Int?
}
