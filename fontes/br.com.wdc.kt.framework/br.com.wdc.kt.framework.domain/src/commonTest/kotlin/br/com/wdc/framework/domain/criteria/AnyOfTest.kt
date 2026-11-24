package br.com.wdc.framework.domain.criteria

import br.com.wdc.framework.domain.SampleCriteria
import br.com.wdc.framework.domain.exception.InvalidRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The alternative groups of a criteria: server-side only, refused on the wire. */
class AnyOfTest {

    private class Plain : Criteria {
        override fun criterions(): List<Criterion<*, *>> = emptyList()
    }

    @Test
    fun aCriteriaThatDoesNotDeclareGroups_hasNone() {
        assertTrue(Plain().anyOf.isEmpty())
        // and it travels as before
        CriterionCodec.refuseAnyOf(Plain())
    }

    @Test
    fun groupsAreKeptInOrder_andTyped() {
        val a = SampleCriteria().name.eq("a")
        val b = SampleCriteria().price.ge(1.5)
        val c = SampleCriteria().withAnyOf(a, b)
        assertEquals(listOf(a, b), c.anyOf)
        val asInterface: Criteria = c
        assertEquals(2, asInterface.anyOf.size)
    }

    @Test
    fun aCriteriaWithGroups_doesNotTravel() {
        CriterionCodec.refuseAnyOf(null)
        CriterionCodec.refuseAnyOf(SampleCriteria())
        val e = assertFailsWith<InvalidRequestException> {
            CriterionCodec.refuseAnyOf(SampleCriteria().withAnyOf(SampleCriteria().name.eq("x")))
        }
        assertTrue("anyOf" in e.message!!, e.message)
    }

    @Test
    fun theReaderFailure_isARequestError() {
        // what a codec throws when it meets the key: 400 in the REST layer, never a skipped key and a looser filter
        val e: Throwable = CriterionCodec.anyOfDoesNotTravel()
        assertTrue(e is InvalidRequestException)
        assertEquals("anyOf", CriterionCodec.ANY_OF)
    }
}
