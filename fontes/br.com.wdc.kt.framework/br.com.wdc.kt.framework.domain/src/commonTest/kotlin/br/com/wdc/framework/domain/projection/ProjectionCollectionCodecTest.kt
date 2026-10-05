package br.com.wdc.framework.domain.projection

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.domain.SampleCriteria
import br.com.wdc.framework.domain.criteria.CriterionCodec
import br.com.wdc.framework.domain.criteria.Operator
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.jsonObject
import br.com.wdc.framework.domain.readerAtField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectionCollectionCodecTest {

    private class Item(var id: Long? = null, var price: Double? = null)

    private val shapeWriter: (ExtensibleObjectOutput, Item) -> Unit = { out, item ->
        out.beginObject()
        item.id?.let { out.name("id").value(it) }
        item.price?.let { out.name("price").value(it) }
        out.endObject()
    }

    private val shapeReader: (ExtensibleObjectInput) -> Item = { input ->
        val item = Item()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> item.id = input.nextLong()
                "price" -> item.price = input.nextDouble()
                else -> input.skipValue()
            }
        }
        input.endObject()
        item
    }

    private val criteriaWriter: (ExtensibleObjectOutput, Any) -> Unit = { out, criteria ->
        criteria as SampleCriteria
        out.beginObject()
        CriterionCodec.write(out, "id", criteria.id, CriterionCodec.LONG_OUT)
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
        out.endObject()
    }

    private val criteriaReader: (ExtensibleObjectInput) -> Any? = { input ->
        val criteria = SampleCriteria()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> CriterionCodec.read(input, criteria.id, CriterionCodec.LONG_IN)
                "orderBy" -> criteria.orderBy = CriterionCodec.readOrderBy(input, SampleCriteria.OrderBy.entries)
                else -> input.skipValue()
            }
        }
        input.endObject()
        criteria
    }

    @Test
    fun write_fullEnvelope() {
        val criteria = SampleCriteria().also { it.id.eq(7L); it.orderBy = SampleCriteria.OrderBy.NAME_A_TO_Z }
        val items = ProjectionValues.singletonList(Item(1L, 1.5), criteria).withOffset(1).withLimit(2)

        val json = jsonObject { ProjectionCollectionCodec.write(it, "items", items, shapeWriter, criteriaWriter) }

        assertEquals(
            """{"items":{"shape":{"id":1,"price":1.5},""" +
                """"where":{"id":{"p":[{"o":"EQ","v":[7]}]},"orderBy":"NAME_A_TO_Z"},"limit":2,"offset":1}}""",
            json,
        )
    }

    @Test
    fun write_shapeOnly_whenNoCriteriaNorSlice() {
        val items = ProjectionValues.singletonList(Item(1L), null)
        val json = jsonObject { ProjectionCollectionCodec.write(it, "items", items, shapeWriter, criteriaWriter) }
        assertEquals("""{"items":{"shape":{"id":1}}}""", json)
    }

    @Test
    fun write_plainCollection_hasNoWhereNorSlice() {
        val json = jsonObject {
            ProjectionCollectionCodec.write(it, "items", listOf(Item(1L)), shapeWriter, criteriaWriter)
        }
        assertEquals("""{"items":{"shape":{"id":1}}}""", json)
    }

    @Test
    fun roundTrip_restoresShapeCriteriaAndSlice() {
        val criteria = SampleCriteria().also { it.id.isIn(3L, 4L); it.orderBy = SampleCriteria.OrderBy.OLDEST_FIRST }
        val source = ProjectionValues.singletonList(Item(1L, 1.0), criteria).withLimit(5).withOffset(10)
        val json = jsonObject { ProjectionCollectionCodec.write(it, "items", source, shapeWriter, criteriaWriter) }

        val reader = readerAtField(json)
        assertTrue(ProjectionCollectionCodec.isProjectionEnvelope(reader))
        val target = ProjectionCollectionCodec.read(reader, shapeReader, criteriaReader)

        assertEquals(1, target.size)
        assertEquals(1L, target[0].id)
        assertEquals(1.0, target[0].price)
        assertEquals(5, target.limit)
        assertEquals(10, target.offset)
        val restored = assertIs<SampleCriteria>(target.criteria)
        assertEquals(Operator.IN, restored.id.predicates.single().operator)
        assertEquals(listOf<Long?>(3L, 4L), restored.id.predicates.single().values)
        assertEquals(SampleCriteria.OrderBy.OLDEST_FIRST, restored.orderBy)
    }

    @Test
    fun read_shapeOnly_leavesCriteriaAndSliceNull() {
        val target = ProjectionCollectionCodec.read(
            readerAtField("""{"items":{"shape":{"id":9},"future":[1,2]}}"""), shapeReader, criteriaReader,
        )
        assertEquals(9L, target.single().id)
        assertNull(target.criteria)
        assertNull(target.limit)
        assertNull(target.offset)
    }

    @Test
    fun read_withoutShape_isRefused() {
        assertFailsWith<InvalidRequestException> {
            ProjectionCollectionCodec.read(readerAtField("""{"items":{"limit":2}}"""), shapeReader, criteriaReader)
        }
    }

    @Test
    fun isProjectionEnvelope_distinguishesObjectFromArray() {
        assertTrue(ProjectionCollectionCodec.isProjectionEnvelope(readerAtField("""{"items":{"shape":{}}}""")))
        assertFalse(ProjectionCollectionCodec.isProjectionEnvelope(readerAtField("""{"items":[{"id":1}]}""")))
    }

    @Test
    fun unknownOrderByInsideWhere_isRefused() {
        val reader = JsonStreamReader("""{"shape":{"id":1},"where":{"orderBy":"SHUFFLED"}}""")
        assertFailsWith<InvalidRequestException> { ProjectionCollectionCodec.read(reader, shapeReader, criteriaReader) }
    }

    @Test
    fun projectionList_holdsOneShape_andComparesAsList() {
        val item = Item(1L)
        val list = ProjectionList(item, "criteria")
        assertEquals(1, list.size)
        assertEquals("criteria", list.criteria)
        assertNull(list.limit)
        assertNull(list.offset)
        // critério e recorte não participam da igualdade
        assertEquals(listOf(item), list.toList())
        assertTrue(list == listOf(item))
        assertEquals(listOf(item).hashCode(), list.hashCode())
        assertTrue(ProjectionList(item, null).withLimit(3) == list)
    }

    @Test
    fun slice_canBeCleared() {
        val list = ProjectionList(Item(1L), null).withLimit(3).withOffset(4)
        list.withLimit(null).withOffset(null)
        assertNull(list.limit)
        assertNull(list.offset)
    }
}
