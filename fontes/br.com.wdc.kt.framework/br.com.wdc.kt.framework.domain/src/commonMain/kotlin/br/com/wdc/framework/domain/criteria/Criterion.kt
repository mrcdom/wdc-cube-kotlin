package br.com.wdc.framework.domain.criteria

import br.com.wdc.framework.domain.exception.InvalidRequestException

/**
 * Criterion on a field: the comparison requests made and how they combine.
 *
 * This class already covers the field whose only sensible comparison is identity — enum, boolean, foreign
 * key. Orderable and text fields use the subclasses, which add what the type allows.
 *
 * **Successive requests accumulate, and by default hold together (`AND`).** This is what makes a range built
 * in two lines — `ge(start)` and then `le(end)`, as it comes from two screen fields — mean what one expects,
 * and what makes `ne(1)` followed by `ne(2)` exclude both.
 *
 * **An alternative is requested with [or]**, which marks the field as disjunctive:
 * ```
 * criteria.name.or().startingWith("CAFE")
 * criteria.name.startingWith("CHA")
 * // (name ILIKE 'CAFE%' OR name ILIKE 'CHA%')
 * ```
 * The disjunction holds **within** the field; between different fields the junction is always `AND`.
 *
 * **A null value adds no request**, instead of erasing the previous ones: it keeps the habit of building a
 * filter from screen fields, where empty means "don't filter by this". To erase what was already requested
 * there is [clear]; to compare with null, [isNull].
 *
 * No method here knows jOOQ or SQL: the same class works on the server and on the client.
 *
 * @param C type of the `XxxCriteria` that contains this field
 * @param T type of the compared value
 * @property name logical name of the field — it is how the translation finds the matching column
 */
open class Criterion<C, T : Any>(owner: C?, val name: String) {

    /** A comparison request: the operator and the values that go with it. */
    class Predicate<T : Any> internal constructor(val operator: Operator, values: List<T?>) {

        val values: List<T?> = values.toList()

        /** First value, or `null` — shortcut for single-value operators. */
        val value: T? get() = values.firstOrNull()

        override fun toString(): String = if (values.isEmpty()) operator.toString() else "$operator $values"
    }

    // Navigation, not state: it doesn't travel (it would close a cycle with the criteria that contains it).
    private var owner: C? = owner

    private val requests = ArrayList<Predicate<T>>()

    /** Requests made on this field, in the order they were made. */
    val predicates: List<Predicate<T>> get() = requests.toList()

    /** `true` when the requests of this field combine with `OR`. */
    var disjunctive: Boolean = false
        private set

    /**
     * Gives the owner back to a criterion that arrived without one — the case of a criterion rebuilt from
     * transport. Without an owner, the fluent methods have nothing to return.
     */
    fun rebind(owner: C) {
        this.owner = owner
    }

    /** `true` when the criterion doesn't know yet which `XxxCriteria` it belongs to. */
    fun detached(): Boolean = owner == null

    /** `true` when there is at least one request, that is, when this field must become a condition. */
    fun isSet(): Boolean = requests.isNotEmpty()

    /**
     * Marks the field as disjunctive: the requests now hold with `OR` among them. It applies to the whole
     * field, not only to the next request — calling it once is enough, at any point.
     */
    open fun or(): Criterion<C, T> {
        disjunctive = true
        return this
    }

    /** Goes back to requiring all requests (`AND`), undoing [or]. */
    open fun and(): Criterion<C, T> {
        disjunctive = false
        return this
    }

    /** Erases the requests of this field, back to not filtering. Doesn't undo [or]. */
    fun clear(): C {
        requests.clear()
        return owner()
    }

    /** Equality. `null` adds no request. */
    fun eq(value: T?): C = if (value == null) owner() else add(Operator.EQ, value)

    /** Not equal to. `null` adds no request. */
    fun ne(value: T?): C = if (value == null) owner() else add(Operator.NE, value)

    /**
     * Belongs to the set. `null` adds no request, as in [eq]: "not provided".
     *
     * **An empty set adds the request, and nothing belongs to it: the search matches no row.** This is what
     * one expects from a list of ids that came empty (the access scope of someone who reaches nothing, a
     * `delete` by the ids of an empty selection). Up to 0.3.0, empty didn't filter, and returned (or deleted)
     * everything.
     *
     * (The operator on the wire is `IN`; the name is `isIn` because `in` is a reserved word in Kotlin.)
     */
    fun isIn(values: Collection<T>?): C {
        if (values == null) {
            return owner()
        }
        requests.add(Predicate(Operator.IN, values.toList()))
        return owner()
    }

    /** Belongs to the set. With no arguments, it is the empty set: matches no row. */
    fun isIn(vararg values: T): C = isIn(values.asList())

    fun isNull(): C = add(Operator.IS_NULL)

    fun isNotNull(): C = add(Operator.IS_NOT_NULL)

    /**
     * Restores a request as it came from transport, bypassing the rules of the fluent methods.
     *
     * It exists for deserialization, and only for it: `eq(null)` adds no request, which is right for whoever
     * builds the filter from a screen, but would silently erase an `EQ NULL` that the other side sent.
     *
     * **Rejects a malformed request** with [InvalidRequestException] (400 in the API): a number of values the
     * operator doesn't accept (`BETWEEN` with one, `EQ` with none) and `null` in an ordering comparison, `LIKE`
     * or `BETWEEN`; and `LIKE`/`ILIKE` on a field that is not text (a [TextCriterion]). If accepted, such a request would count as a provided filter and vanish from the SQL — a
     * `delete` "with criteria" would wipe the whole table. `EQ`/`NE` with `null` are valid
     * (`IS NULL`/`IS NOT NULL`).
     */
    fun restore(operator: Operator, values: List<T?>?) {
        val list = values ?: emptyList()
        if (!operator.accepts(list.size)) {
            throw InvalidRequestException("criterion '$name': $operator doesn't accept ${list.size} value(s)")
        }
        if (operator in NOT_NULL_OPERANDS && list.any { it == null }) {
            throw InvalidRequestException("criterion '$name': $operator doesn't accept a null value")
        }
        if ((operator == Operator.LIKE || operator == Operator.ILIKE) && this !is TextCriterion<*>) {
            throw InvalidRequestException("criterion '$name': $operator only applies to text")
        }
        requests.add(Predicate(operator, list))
    }

    /** Marks the disjunction when rebuilding the criterion from transport. */
    fun restoreDisjunctive(value: Boolean) {
        disjunctive = value
    }

    /** Adds a request; visible to subclasses, which offer the operators according to the field type. */
    protected fun add(operator: Operator, vararg values: T?): C {
        requests.add(Predicate(operator, values.asList()))
        return owner()
    }

    /** The owning criteria, for subclasses to return when chaining. */
    protected fun owner(): C =
        owner ?: throw IllegalStateException("criterion '$name' has no owner — call rebind() before chaining")

    override fun toString(): String =
        if (!isSet()) "$name (not provided)"
        else "$name ${if (disjunctive) "any of " else "all of "}$requests"

    private companion object {
        /** Operators where `null` makes no sense: a comparison with null is never true. */
        val NOT_NULL_OPERANDS = setOf(Operator.GT, Operator.GE, Operator.LT, Operator.LE, Operator.LIKE, Operator.ILIKE, Operator.BETWEEN)
    }
}
