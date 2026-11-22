package br.com.wdc.framework.domain.criteria

/**
 * Criterion on an orderable field — number, date, timestamp.
 *
 * Adds to equality what only makes sense where there is order. The split into a subclass is what keeps
 * `criteria.roles.between(...)` from compiling: whoever declares the field knows the column type and picks the
 * class, so the restriction costs nothing to whoever writes the criteria and prevents the error at the source.
 *
 * @param C type of the `XxxCriteria` that contains this field
 * @param T type of the compared value
 */
open class ComparableCriterion<C, T : Any>(owner: C?, name: String) : Criterion<C, T>(owner, name) {

    // Covariant return: without this, or() would return Criterion and chaining would lose between and the comparisons.
    override fun or(): ComparableCriterion<C, T> {
        super.or()
        return this
    }

    override fun and(): ComparableCriterion<C, T> {
        super.and()
        return this
    }

    /** Greater than. `null` adds no request. */
    fun gt(value: T?): C = if (value == null) owner() else add(Operator.GT, value)

    /** Greater than or equal to. `null` adds no request. */
    fun ge(value: T?): C = if (value == null) owner() else add(Operator.GE, value)

    /** Less than. `null` adds no request. */
    fun lt(value: T?): C = if (value == null) owner() else add(Operator.LT, value)

    /** Less than or equal to. `null` adds no request. */
    fun le(value: T?): C = if (value == null) owner() else add(Operator.LE, value)

    /**
     * Range closed at both ends.
     *
     * **Accepts an open bound.** Only the start becomes `>=`; only the end becomes `<=`; with neither, no
     * request is added. It is the common case of a period filter on screen, where usually only one date is filled.
     */
    fun between(start: T?, end: T?): C = when {
        start == null && end == null -> owner()
        start == null -> le(end)
        end == null -> ge(start)
        else -> add(Operator.BETWEEN, start, end)
    }
}
