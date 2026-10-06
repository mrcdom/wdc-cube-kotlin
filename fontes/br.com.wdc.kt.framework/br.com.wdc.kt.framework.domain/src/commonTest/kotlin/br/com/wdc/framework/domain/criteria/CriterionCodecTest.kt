package br.com.wdc.framework.domain.criteria

import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.domain.SampleCriteria
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.jsonObject
import br.com.wdc.framework.domain.readerAtField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CriterionCodecTest {

    @Test
    fun write_unsetField_emitsNoKey() {
        val c = SampleCriteria()
        val json = jsonObject { CriterionCodec.write(it, "price", c.price, CriterionCodec.DOUBLE_OUT) }
        assertEquals("{}", json)
    }

    @Test
    fun write_usesTheWireFormat() {
        val c = SampleCriteria()
        // valor não inteiro: em JS um Double inteiro é escrito sem a parte decimal (10.0 → 10)
        c.price.or().ge(10.5)
        c.price.isNull()
        val json = jsonObject { CriterionCodec.write(it, "price", c.price, CriterionCodec.DOUBLE_OUT) }
        assertEquals("""{"price":{"or":true,"p":[{"o":"GE","v":[10.5]},{"o":"IS_NULL"}]}}""", json)
    }

    @Test
    fun write_conjunctiveField_omitsOr() {
        val c = SampleCriteria()
        c.id.isIn(1L, 2L)
        val json = jsonObject { CriterionCodec.write(it, "id", c.id, CriterionCodec.LONG_OUT) }
        assertEquals("""{"id":{"p":[{"o":"IN","v":[1,2]}]}}""", json)
    }

    @Test
    fun roundTrip_keepsOperatorsValuesAndDisjunction() {
        val source = SampleCriteria()
        source.price.or().between(1.5, 9.5)
        source.price.ne(4.0)
        source.price.isIn(2.0, 3.0)
        source.price.isNotNull()
        val json = jsonObject { CriterionCodec.write(it, "price", source.price, CriterionCodec.DOUBLE_OUT) }

        val target = SampleCriteria()
        CriterionCodec.read(readerAtField(json), target.price, CriterionCodec.DOUBLE_IN)

        assertTrue(target.price.disjunctive)
        assertEquals(source.price.predicates.map { it.operator }, target.price.predicates.map { it.operator })
        assertEquals(source.price.predicates.map { it.values }, target.price.predicates.map { it.values })
    }

    @Test
    fun roundTrip_text_keepsWildcards() {
        val source = SampleCriteria()
        source.name.containing("ca\"fé")
        source.name.like("A_%")
        val json = jsonObject { CriterionCodec.write(it, "name", source.name, CriterionCodec.STRING_OUT) }

        val target = SampleCriteria()
        CriterionCodec.read(readerAtField(json), target.name, CriterionCodec.STRING_IN)

        assertEquals(listOf(Operator.ILIKE, Operator.LIKE), target.name.predicates.map { it.operator })
        assertEquals(listOf<String?>("%ca\"fé%", "A_%"), target.name.predicates.map { it.value })
    }

    @Test
    fun roundTrip_nullInsideValues_survives() {
        val source = SampleCriteria()
        source.id.restore(Operator.EQ, listOf(null))
        val json = jsonObject { CriterionCodec.write(it, "id", source.id, CriterionCodec.LONG_OUT) }
        assertEquals("""{"id":{"p":[{"o":"EQ","v":[null]}]}}""", json)

        val target = SampleCriteria()
        CriterionCodec.read(readerAtField(json), target.id, CriterionCodec.LONG_IN)
        assertEquals(listOf<Long?>(null), target.id.predicates.single().values)
    }

    @Test
    fun read_looseValue_isEquality() {
        val c = SampleCriteria()
        CriterionCodec.read(readerAtField("""{"id":42}"""), c.id, CriterionCodec.LONG_IN)
        val p = c.id.predicates.single()
        assertEquals(Operator.EQ, p.operator)
        assertEquals(42L, p.value)
    }

    @Test
    fun read_looseNull_isDiscarded() {
        val c = SampleCriteria()
        CriterionCodec.read(readerAtField("""{"id":null}"""), c.id, CriterionCodec.LONG_IN)
        assertFalse(c.id.isSet())
    }

    @Test
    fun read_unknownOperator_isDiscarded_othersAreKept() {
        val c = SampleCriteria()
        val json = """{"id":{"p":[{"o":"REGEXP","v":[1]},{"o":"GT","v":[5]}]}}"""
        CriterionCodec.read(readerAtField(json), c.id, CriterionCodec.LONG_IN)
        val p = c.id.predicates.single()
        assertEquals(Operator.GT, p.operator)
        assertEquals(5L, p.value)
    }

    @Test
    fun read_unknownKeys_areSkipped() {
        val c = SampleCriteria()
        val json = """{"id":{"future":{"a":[1,2]},"p":[{"o":"EQ","x":true,"v":[7]}],"or":false}}"""
        CriterionCodec.read(readerAtField(json), c.id, CriterionCodec.LONG_IN)
        assertEquals(7L, c.id.predicates.single().value)
        assertFalse(c.id.disjunctive)
    }

    @Test
    fun read_valuesBeforeOperator_stillWork() {
        val c = SampleCriteria()
        CriterionCodec.read(readerAtField("""{"id":{"p":[{"v":[1,9],"o":"BETWEEN"}]}}"""), c.id, CriterionCodec.LONG_IN)
        val p = c.id.predicates.single()
        assertEquals(Operator.BETWEEN, p.operator)
        assertEquals(listOf<Long?>(1L, 9L), p.values)
    }

    @Test
    fun readers_coerceNumbersSentAsText() {
        val c = SampleCriteria()
        CriterionCodec.read(readerAtField("""{"id":{"p":[{"o":"EQ","v":["15"]}]}}"""), c.id, CriterionCodec.LONG_IN)
        assertEquals(15L, c.id.predicates.single().value)
    }

    @Test
    fun instant_writesUtc_andReadsOffsets() {
        val instant = Instant.parse("2026-10-05T03:30:00Z")
        val holder = ComparableCriterion<SampleCriteria, Instant>(SampleCriteria(), "when")
        holder.ge(instant)
        val json = jsonObject { CriterionCodec.write(it, "when", holder, CriterionCodec.INSTANT_OUT) }
        assertEquals("""{"when":{"p":[{"o":"GE","v":["2026-10-05T03:30:00Z"]}]}}""", json)

        // um cliente Java manda ISO_OFFSET_DATE_TIME
        val fromOffset = ComparableCriterion<SampleCriteria, Instant>(SampleCriteria(), "when")
        CriterionCodec.read(
            readerAtField("""{"when":{"p":[{"o":"GE","v":["2026-10-05T00:30:00-03:00"]}]}}"""),
            fromOffset, CriterionCodec.INSTANT_IN,
        )
        assertEquals(instant, fromOffset.predicates.single().value)
    }

    @Test
    fun readOrderBy_acceptsEveryConstant() {
        for (expected in SampleCriteria.OrderBy.entries) {
            val reader = readerAtField("""{"orderBy":"${expected.name}"}""")
            assertEquals(expected, CriterionCodec.readOrderBy(reader, SampleCriteria.OrderBy.entries))
        }
    }

    @Test
    fun readOrderBy_null_isNull() {
        val reader = readerAtField("""{"orderBy":null}""")
        assertNull(CriterionCodec.readOrderBy(reader, SampleCriteria.OrderBy.entries))
    }

    @Test
    fun readOrderBy_unknownName_isRefused_namingValueAndAccepted() {
        val reader = readerAtField("""{"orderBy":"RANDOM"}""")
        val e = assertFailsWith<InvalidRequestException> {
            CriterionCodec.readOrderBy(reader, SampleCriteria.OrderBy.entries)
        }
        assertEquals("ordenação desconhecida: 'RANDOM' — aceitas: OLDEST_FIRST, NAME_A_TO_Z", e.message)
    }

    @Test
    fun wholeCriteria_roundTrip() {
        val source = SampleCriteria()
        source.id.isIn(1L, 2L)
        source.name.or().startingWith("A")
        source.active.eq(true)

        val json = jsonObject { out ->
            CriterionCodec.write(out, "id", source.id, CriterionCodec.LONG_OUT)
            CriterionCodec.write(out, "price", source.price, CriterionCodec.DOUBLE_OUT)
            CriterionCodec.write(out, "name", source.name, CriterionCodec.STRING_OUT)
            CriterionCodec.write(out, "active", source.active, CriterionCodec.BOOL_OUT)
        }

        val target = SampleCriteria()
        val reader = JsonStreamReader(json)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> CriterionCodec.read(reader, target.id, CriterionCodec.LONG_IN)
                "price" -> CriterionCodec.read(reader, target.price, CriterionCodec.DOUBLE_IN)
                "name" -> CriterionCodec.read(reader, target.name, CriterionCodec.STRING_IN)
                "active" -> CriterionCodec.read(reader, target.active, CriterionCodec.BOOL_IN)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        assertEquals(listOf("id", "name", "active"), target.criterions().filter { it.isSet() }.map { it.name })
        assertEquals(listOf<Long?>(1L, 2L), target.id.predicates.single().values)
        assertTrue(target.name.disjunctive)
        assertEquals("A%", target.name.predicates.single().value)
        assertEquals(true, target.active.predicates.single().value)
    }
}
