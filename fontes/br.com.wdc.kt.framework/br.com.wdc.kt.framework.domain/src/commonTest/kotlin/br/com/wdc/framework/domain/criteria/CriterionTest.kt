package br.com.wdc.framework.domain.criteria

import br.com.wdc.framework.domain.SampleCriteria
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CriterionTest {

    @Test
    fun newCriterion_isNotSet() {
        val c = SampleCriteria()
        assertFalse(c.id.isSet())
        assertTrue(c.id.predicates.isEmpty())
        assertFalse(c.id.disjunctive)
        assertEquals("id", c.id.name)
    }

    @Test
    fun fluentMethods_returnTheOwner() {
        val c = SampleCriteria()
        assertSame(c, c.id.eq(1L))
        assertSame(c, c.id.eq(null))
        assertSame(c, c.price.between(1.0, 2.0))
        assertSame(c, c.name.containing("x"))
        assertSame(c, c.id.clear())
    }

    @Test
    fun successiveRequests_accumulate() {
        val c = SampleCriteria()
        c.price.ge(10.0)
        c.price.le(20.0)
        assertEquals(listOf(Operator.GE, Operator.LE), c.price.predicates.map { it.operator })
        assertEquals(listOf(10.0, 20.0), c.price.predicates.map { it.value })
        assertFalse(c.price.disjunctive)
    }

    @Test
    fun nullValue_addsNoRequest_andKeepsPreviousOnes() {
        val c = SampleCriteria()
        c.id.ne(1L)
        c.id.eq(null)
        c.id.ne(null)
        c.id.gt(null)
        c.id.ge(null)
        c.id.lt(null)
        c.id.le(null)
        assertEquals(1, c.id.predicates.size)
        assertEquals(Operator.NE, c.id.predicates[0].operator)
    }

    @Test
    fun isIn_ignoresNullAndEmpty() {
        val c = SampleCriteria()
        c.id.isIn(null as Collection<Long>?)
        c.id.isIn(emptyList())
        assertFalse(c.id.isSet())

        c.id.isIn(1L, 2L, 3L)
        c.id.isIn(listOf(4L))
        assertEquals(2, c.id.predicates.size)
        assertEquals(Operator.IN, c.id.predicates[0].operator)
        assertEquals(listOf<Long?>(1L, 2L, 3L), c.id.predicates[0].values)
        assertEquals(listOf<Long?>(4L), c.id.predicates[1].values)
    }

    @Test
    fun isIn_copiesTheCollection() {
        val c = SampleCriteria()
        val source = mutableListOf(1L, 2L)
        c.id.isIn(source)
        source.add(3L)
        assertEquals(listOf<Long?>(1L, 2L), c.id.predicates[0].values)
    }

    @Test
    fun isNull_andIsNotNull_haveNoValues() {
        val c = SampleCriteria()
        c.active.isNull()
        c.active.isNotNull()
        assertEquals(listOf(Operator.IS_NULL, Operator.IS_NOT_NULL), c.active.predicates.map { it.operator })
        assertTrue(c.active.predicates.all { it.values.isEmpty() && it.value == null })
    }

    @Test
    fun or_marksTheWholeField_andIsIdempotent() {
        val c = SampleCriteria()
        c.name.or().startingWith("CAFE")
        c.name.startingWith("CHA")
        c.name.or()
        assertTrue(c.name.disjunctive)
        assertEquals(2, c.name.predicates.size)
        // a disjunção é do campo; os outros campos não são afetados
        assertFalse(c.id.disjunctive)
    }

    @Test
    fun and_undoesOr() {
        val c = SampleCriteria()
        c.price.or().and()
        assertFalse(c.price.disjunctive)
    }

    @Test
    fun or_keepsTheSubclassOperatorsInTheChain() {
        val c = SampleCriteria()
        // só compila se or() devolver o tipo mais específico
        assertSame(c, c.price.or().between(1.0, 2.0))
        assertSame(c, c.name.or().like("A%"))
        assertSame(c, c.name.and().ilike("a%"))
    }

    @Test
    fun clear_removesRequests_butKeepsOr() {
        val c = SampleCriteria()
        c.price.or().ge(1.0)
        c.price.clear()
        assertFalse(c.price.isSet())
        assertTrue(c.price.disjunctive)
    }

    @Test
    fun between_withBothBounds_isBetween() {
        val c = SampleCriteria()
        c.price.between(1.0, 2.0)
        val p = c.price.predicates.single()
        assertEquals(Operator.BETWEEN, p.operator)
        assertEquals(listOf<Double?>(1.0, 2.0), p.values)
    }

    @Test
    fun between_withOpenBounds() {
        val onlyStart = SampleCriteria().also { it.price.between(1.0, null) }
        assertEquals(Operator.GE, onlyStart.price.predicates.single().operator)
        assertEquals(1.0, onlyStart.price.predicates.single().value)

        val onlyEnd = SampleCriteria().also { it.price.between(null, 2.0) }
        assertEquals(Operator.LE, onlyEnd.price.predicates.single().operator)
        assertEquals(2.0, onlyEnd.price.predicates.single().value)

        val none = SampleCriteria().also { it.price.between(null, null) }
        assertFalse(none.price.isSet())
    }

    @Test
    fun like_andIlike_keepWildcardsAsGiven() {
        val c = SampleCriteria()
        c.name.like("CAFE")
        c.name.ilike("%cha_")
        assertEquals(listOf(Operator.LIKE, Operator.ILIKE), c.name.predicates.map { it.operator })
        assertEquals(listOf<String?>("CAFE", "%cha_"), c.name.predicates.map { it.value })
    }

    @Test
    fun containing_andStartingWith_areIlikeWithWildcards() {
        val c = SampleCriteria()
        c.name.containing("afe")
        c.name.startingWith("Ca")
        assertTrue(c.name.predicates.all { it.operator == Operator.ILIKE })
        assertEquals(listOf<String?>("%afe%", "Ca%"), c.name.predicates.map { it.value })
    }

    @Test
    fun emptyOrNullText_addsNoRequest() {
        val c = SampleCriteria()
        c.name.like(null); c.name.like("")
        c.name.ilike(null); c.name.ilike("")
        c.name.containing(null); c.name.containing("")
        c.name.startingWith(null); c.name.startingWith("")
        assertFalse(c.name.isSet())
    }

    @Test
    fun restore_bypassesTheFluentRules() {
        val c = SampleCriteria()
        c.id.restore(Operator.EQ, listOf(null))
        c.id.restore(Operator.IS_NULL, null)
        c.id.restoreDisjunctive(true)
        assertEquals(2, c.id.predicates.size)
        assertEquals(listOf<Long?>(null), c.id.predicates[0].values)
        assertTrue(c.id.predicates[1].values.isEmpty())
        assertTrue(c.id.disjunctive)
    }

    @Test
    fun predicates_areASnapshot() {
        val c = SampleCriteria()
        c.id.eq(1L)
        val before = c.id.predicates
        c.id.eq(2L)
        assertEquals(1, before.size)
        assertEquals(2, c.id.predicates.size)
    }

    @Test
    fun detachedCriterion_failsOnChaining_untilRebound() {
        val criterion = ComparableCriterion<SampleCriteria, Long>(null, "id")
        assertTrue(criterion.detached())
        assertFailsWith<IllegalStateException> { criterion.eq(1L) }

        val owner = SampleCriteria()
        criterion.rebind(owner)
        assertFalse(criterion.detached())
        assertSame(owner, criterion.eq(2L))
    }

    @Test
    fun criterions_listsEveryField_inDeclarationOrder() {
        val c = SampleCriteria()
        c.name.eq("x")
        assertEquals(listOf("id", "price", "name", "active"), c.criterions().map { it.name })
        assertEquals(listOf("name"), c.criterions().filter { it.isSet() }.map { it.name })
    }

    @Test
    fun operator_accepts_followsArity() {
        assertTrue(Operator.EQ.accepts(1)); assertFalse(Operator.EQ.accepts(0)); assertFalse(Operator.EQ.accepts(2))
        assertTrue(Operator.BETWEEN.accepts(2)); assertFalse(Operator.BETWEEN.accepts(1))
        assertTrue(Operator.IN.accepts(1)); assertTrue(Operator.IN.accepts(7)); assertFalse(Operator.IN.accepts(0))
        assertTrue(Operator.IS_NULL.accepts(0)); assertFalse(Operator.IS_NULL.accepts(1))
    }
}
