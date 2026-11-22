package br.com.wdc.framework.domain.projection

/**
 * Projection collection of a 1:N relation — carries the projected bean (exactly one element, the item shape),
 * the child entity's criteria and, optionally, the slice to apply.
 *
 * The criteria also carries the `OrderBy`, and the order comes from it. Without an order, [limit] and [offset]
 * cut rows in undefined order — see [HasSlice].
 *
 * @param bean the item shape.
 * @param criteria the child entity's criteria (with the order).
 * @param limit maximum child rows to bring, or `null` for all. Order too, or the cut is arbitrary.
 * @param offset child rows to skip before starting, or `null` for none. Order too, or the skip is
 *   arbitrary.
 */
class ProjectionList<E> private constructor(
    private val delegate: MutableList<E>,
    override val criteria: Any?,
    override val limit: Int?,
    override val offset: Int?,
) : MutableList<E> by delegate, HasCriteria, HasSlice {

    constructor(bean: E, criteria: Any?, limit: Int? = null, offset: Int? = null) :
        this(ArrayList<E>(1), criteria, limit, offset) {
        add(bean)
    }

    /** Copy with another maximum of child rows. */
    @Deprecated(
        "Pass the slice to the constructor; removed in the next version",
        ReplaceWith("ProjectionList(first(), criteria, limit = limit, offset = offset)"),
    )
    fun withLimit(limit: Int?): ProjectionList<E> = ProjectionList(ArrayList(delegate), criteria, limit, offset)

    /** Copy with another skip of child rows. */
    @Deprecated(
        "Pass the slice to the constructor; removed in the next version",
        ReplaceWith("ProjectionList(first(), criteria, limit = limit, offset = offset)"),
    )
    fun withOffset(offset: Int?): ProjectionList<E> = ProjectionList(ArrayList(delegate), criteria, limit, offset)

    // List equality: criteria and slice don't take part.
    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}
