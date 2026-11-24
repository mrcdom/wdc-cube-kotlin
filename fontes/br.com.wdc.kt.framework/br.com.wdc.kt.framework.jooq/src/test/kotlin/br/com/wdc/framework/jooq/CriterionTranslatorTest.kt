package br.com.wdc.framework.jooq

import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jooq.Condition
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

/** The translation of a field that applies to several columns. */
class CriterionTranslatorTest {

    private class Search : Criteria {
        val term = TextCriterion(this, "term")
        override fun criterions(): List<Criterion<*, *>> = listOf(term)

        override var anyOf: List<Search> = emptyList()
            private set

        fun withAnyOf(vararg groups: Search) = apply { anyOf = groups.toList() }
    }

    /** How a repository translates a criteria: its fields in AND, and the groups — translated the same way — in OR. */
    private fun conditions(s: Search): Condition = CriterionTranslator.and(
        listOf(
            CriterionTranslator.translate(name, s.term),
            CriterionTranslator.anyOf(s.anyOf, ::conditions),
        ),
    )

    private val dsl = DSL.using(SQLDialect.POSTGRES)
    private val name = DSL.field(DSL.name("name"), SQLDataType.VARCHAR)
    private val code = DSL.field(DSL.name("code"), SQLDataType.VARCHAR)

    private fun sql(c: Condition?) = c?.let { dsl.renderInlined(it) }

    @Test
    fun translateAny_appliesTheFieldToEachColumn_joinedByOr() {
        val c = Search().term.containing("ab")
        assertEquals(
            sql(DSL.or(name.likeIgnoreCase("%ab%", TextCriterion.ESCAPE), code.likeIgnoreCase("%ab%", TextCriterion.ESCAPE))),
            sql(CriterionTranslator.translateAny(listOf(name, code), c.term)),
        )
    }

    @Test
    fun translateAny_keepsEveryRequestOfTheFieldInsideEachColumn() {
        val c = Search().term.startingWith("a").term.containing("z")
        val each = { f: org.jooq.Field<String?> -> DSL.and(f.likeIgnoreCase("a%", TextCriterion.ESCAPE), f.likeIgnoreCase("%z%", TextCriterion.ESCAPE)) }
        assertEquals(sql(DSL.or(each(name), each(code))), sql(CriterionTranslator.translateAny(listOf(name, code), c.term)))
    }

    @Test
    fun translateAny_withOneColumn_isTheSameAsTranslate() {
        val c = Search().term.eq("x")
        assertEquals(sql(CriterionTranslator.translate(name, c.term)), sql(CriterionTranslator.translateAny(listOf(name), c.term)))
    }

    @Test
    fun emptyIn_isFalse_alsoOverSeveralColumns() {
        val empty = Search().term.isIn(emptyList()).term
        assertEquals(sql(DSL.falseCondition()), sql(CriterionTranslator.translate(name, empty)))
        assertEquals(
            sql(DSL.or(DSL.falseCondition(), DSL.falseCondition())),
            sql(CriterionTranslator.translateAny(listOf(name, code), empty)),
        )
        // inside a disjunctive field, the empty one does not hide the other predicates
        val alternative = Search().term.or().eq("x").term.isIn().term
        assertEquals(sql(DSL.or(name.eq("x"), DSL.falseCondition())), sql(CriterionTranslator.translate(name, alternative)))
    }

    @Test
    fun translateAny_withoutRequestOrColumns_isNull() {
        assertNull(CriterionTranslator.translateAny(listOf(name, code), Search().term))
        assertNull(CriterionTranslator.translateAny(emptyList<org.jooq.Field<String?>>(), Search().term.eq("x").term))
    }

    // :: anyOf

    @Test
    fun anyOf_joinsTheGroupsWithOr() {
        val a = Search().term.eq("a")
        val b = Search().term.eq("b")
        assertEquals(sql(DSL.or(name.eq("a"), name.eq("b"))), sql(CriterionTranslator.anyOf(listOf(a, b)) { CriterionTranslator.translate(name, it.term) }))
    }

    @Test
    fun anyOf_aGroupWithNoCondition_matchesEveryRow() {
        val a = Search().term.eq("a")
        // a group that does not filter makes the whole alternative hold
        assertNull(CriterionTranslator.anyOf(listOf(a, Search()), ::conditions))
    }

    @Test
    fun anyOf_withoutGroups_isNull() {
        assertNull(CriterionTranslator.anyOf(emptyList<Search>()) { DSL.trueCondition() })
        assertNull(CriterionTranslator.anyOf(null as List<Search>?) { DSL.trueCondition() })
    }

    @Test
    fun anyOf_isConjunctiveWithTheOuterFields_andGroupsNest() {
        val inner = Search().term.eq("c")
        val group = Search().term.eq("b").withAnyOf(inner)
        val criteria = Search().term.ne("z").withAnyOf(Search().term.eq("a"), group)
        assertEquals(
            sql(DSL.and(name.ne("z"), DSL.or(name.eq("a"), DSL.and(name.eq("b"), name.eq("c"))))),
            sql(conditions(criteria)),
        )
    }
}
