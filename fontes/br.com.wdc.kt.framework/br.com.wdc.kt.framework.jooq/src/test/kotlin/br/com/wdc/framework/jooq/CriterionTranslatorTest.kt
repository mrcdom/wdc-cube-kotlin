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

/** A tradução de um campo que incide sobre várias colunas. */
class CriterionTranslatorTest {

    private class Search : Criteria {
        val term = TextCriterion(this, "term")
        override fun criterions(): List<Criterion<*, *>> = listOf(term)
    }

    private val dsl = DSL.using(SQLDialect.POSTGRES)
    private val name = DSL.field(DSL.name("name"), SQLDataType.VARCHAR)
    private val code = DSL.field(DSL.name("code"), SQLDataType.VARCHAR)

    private fun sql(c: Condition?) = c?.let { dsl.renderInlined(it) }

    @Test
    fun translateAny_appliesTheFieldToEachColumn_joinedByOr() {
        val c = Search().term.containing("ab")
        assertEquals(
            sql(DSL.or(name.likeIgnoreCase("%ab%"), code.likeIgnoreCase("%ab%"))),
            sql(CriterionTranslator.translateAny(listOf(name, code), c.term)),
        )
    }

    @Test
    fun translateAny_keepsEveryRequestOfTheFieldInsideEachColumn() {
        val c = Search().term.startingWith("a").term.containing("z")
        val each = { f: org.jooq.Field<String?> -> DSL.and(f.likeIgnoreCase("a%"), f.likeIgnoreCase("%z%")) }
        assertEquals(sql(DSL.or(each(name), each(code))), sql(CriterionTranslator.translateAny(listOf(name, code), c.term)))
    }

    @Test
    fun translateAny_withOneColumn_isTheSameAsTranslate() {
        val c = Search().term.eq("x")
        assertEquals(sql(CriterionTranslator.translate(name, c.term)), sql(CriterionTranslator.translateAny(listOf(name), c.term)))
    }

    @Test
    fun translateAny_withoutRequestOrColumns_isNull() {
        assertNull(CriterionTranslator.translateAny(listOf(name, code), Search().term))
        assertNull(CriterionTranslator.translateAny(emptyList<org.jooq.Field<String?>>(), Search().term.eq("x").term))
    }
}
