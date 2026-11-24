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

    /**
     * The alternative groups: the criteria matches when its own fields match **and** at least one group matches.
     *
     * The fields of a criteria always join with `AND`, and `or()` only joins the requests of a single field. A
     * disjunction across fields — "my purchases, or the ones of my store" — needs groups: each one is a criteria of
     * the same entity, with its fields in `AND`, and the groups join with `OR`. A group may have groups of its own. An
     * empty list means no alternative; a group with no field set matches every row.
     *
     * Only the filter fields of a group count: its projection and its ordering are ignored, since the query has one
     * of each, and they belong to the outer criteria.
     *
     * **The groups are for code on the server** — the data policy of `framework-rest` uses them to restrict the rows
     * a role reaches — and do not travel: the codecs refuse them on both sides of the wire
     * ([CriterionCodec.refuseAnyOf], [CriterionCodec.anyOfDoesNotTravel]). A disjunction chosen by the client would be
     * any tree of `OR`s over any columns; what a client needs, a term searched in several columns, is a criteria field
     * of its own, translated by the repository.
     *
     * The default is no groups: a criteria that supports them overrides it with its own type and offers a
     * `withAnyOf(...)`.
     */
    val anyOf: List<Criteria> get() = emptyList()
}
