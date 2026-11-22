package br.com.wdc.framework.domain.criteria

/**
 * Contract shared by every `XxxCriteria`: exposes the filterable fields as a list.
 *
 * It exists so that translating the criteria into a condition is written **once**: the core walks the
 * provided fields, and what is left to each entity is saying which column matches each name.
 */
interface Criteria {

    /**
     * The criteria fields, in declaration order — which is the order in which the conditions enter the `AND`.
     * Includes all of them, provided or not; tell them apart with [Criterion.isSet].
     */
    fun criterions(): List<Criterion<*, *>>
}
