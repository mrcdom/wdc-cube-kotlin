package br.com.wdc.framework.domain.criteria

/**
 * Comparison operator of a [Criterion], with the same semantics as SQL.
 *
 * It is deliberately **neutral**: it knows neither jOOQ nor SQL, it only names the comparison. The translation
 * into a condition belongs to `framework-jooq`; the REST client serializes the same enum.
 *
 * @property arity number of values the operator requires; negative when variable ([IN], which also accepts
 *   none)
 */
enum class Operator(val arity: Int) {
    /** `= ?` */
    EQ(1),
    /** `<> ?` */
    NE(1),
    /** `> ?` */
    GT(1),
    /** `>= ?` */
    GE(1),
    /** `< ?` */
    LT(1),
    /** `<= ?` */
    LE(1),
    /** `LIKE ?` — case-sensitive; wildcards are part of the value. */
    LIKE(1),
    /** `ILIKE ?` — case-insensitive. */
    ILIKE(1),
    /** `BETWEEN ? AND ?` */
    BETWEEN(2),
    /** `IN (?, ?, …)` — variable arity. Empty is a set with no members: matches no row. */
    IN(-1),
    /** `IS NULL` */
    IS_NULL(0),
    /** `IS NOT NULL` */
    IS_NOT_NULL(0);

    /** `true` when the given count satisfies the operator. */
    fun accepts(count: Int): Boolean = if (arity < 0) count >= 0 else count == arity
}
