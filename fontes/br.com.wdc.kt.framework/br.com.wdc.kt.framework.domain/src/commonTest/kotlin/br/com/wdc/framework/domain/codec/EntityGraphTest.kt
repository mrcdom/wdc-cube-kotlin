package br.com.wdc.framework.domain.codec

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.commons.serialization.JsonStreamWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EntityGraphTest {

    private class Order(var id: Long? = null, var lines: MutableList<Line>? = null) : KeyedEntity {
        override fun key(): Any? = id
    }

    private class Line(var id: Long? = null, var order: Order? = null) : KeyedEntity {
        override fun key(): Any? = id
    }

    /** Codec mínimo, só com o que o ciclo Order ↔ Line exige. */
    private object OrderCodec : ModelCodec<Order, Unit> {

        override fun writeEntity(out: ExtensibleObjectOutput, entity: Order, graph: EntityGraph) {
            out.beginObject()
            entity.id?.let { out.name("id").value(it) }
            if (graph.track(entity)) {
                entity.lines?.let { lines ->
                    out.name("lines").beginArray()
                    for (line in lines) {
                        out.beginObject()
                        line.id?.let { out.name("id").value(it) }
                        if (graph.track(line)) {
                            line.order?.let { out.name("order"); writeEntity(out, it, graph) }
                        }
                        out.endObject()
                    }
                    out.endArray()
                }
            }
            out.endObject()
        }

        override fun readEntity(input: ExtensibleObjectInput): Order {
            val order = Order()
            input.beginObject()
            while (input.hasNext()) {
                when (input.nextName()) {
                    "id" -> order.id = input.nextLong()
                    "lines" -> {
                        val lines = ArrayList<Line>()
                        input.beginArray()
                        while (input.hasNext()) {
                            val line = Line()
                            input.beginObject()
                            while (input.hasNext()) {
                                when (input.nextName()) {
                                    "id" -> line.id = input.nextLong()
                                    else -> input.skipValue()
                                }
                            }
                            input.endObject()
                            // back-reference em stub: só a chave do pai
                            line.order = Order(order.id)
                            lines.add(line)
                        }
                        input.endArray()
                        order.lines = lines
                    }
                    else -> input.skipValue()
                }
            }
            input.endObject()
            return order
        }

        override fun writeEntityProjected(out: ExtensibleObjectOutput, entity: Order, projection: Order) =
            throw UnsupportedOperationException()
        override fun computeProjection(newEntity: Order, oldEntity: Order): Order = throw UnsupportedOperationException()
        override fun readEntityForUpdate(input: ExtensibleObjectInput): ModelCodec.UpdateData<Order> =
            throw UnsupportedOperationException()
        override fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: Unit) = Unit
        override fun readCriteriaField(input: ExtensibleObjectInput, fieldName: String, criteria: Unit): Boolean = false
        override fun getProjection(criteria: Unit): Order? = null
        override fun setGeneratedId(entity: Order, id: Long) { entity.id = id }
    }

    @Test
    fun track_isTrueOnce_perInstance() {
        val graph = EntityGraph()
        val order = Order(1L)
        assertFalse(graph.isSeen(order))
        assertTrue(graph.track(order))
        assertFalse(graph.track(order))
        assertTrue(graph.isSeen(order))
    }

    @Test
    fun track_usesIdentity_notTheKey() {
        val graph = EntityGraph()
        val first = Order(1L)
        val sameKey = Order(1L)
        assertTrue(graph.track(first))
        assertTrue(graph.track(sameKey))
        assertFalse(graph.track(first))
        assertFalse(graph.track(sameKey))
    }

    @Test
    fun track_acceptsNullKeys_andNullEntities() {
        val graph = EntityGraph()
        val unsavedA = Order()
        val unsavedB = Order()
        assertTrue(graph.track(unsavedA))
        assertTrue(graph.track(unsavedB))
        assertFalse(graph.track(unsavedA))

        assertTrue(graph.track(null))
        assertTrue(graph.track(null))
        assertFalse(graph.isSeen(null))
    }

    @Test
    fun writeEntity_withCycle_writesTheRepeatedInstanceAsStub() {
        val order = Order(1L)
        order.lines = mutableListOf(Line(10L, order), Line(11L, order))

        val writer = JsonStreamWriter()
        OrderCodec.writeEntity(writer, order)

        // sem o rastreamento, isto não terminaria
        assertEquals("""{"id":1,"lines":[{"id":10,"order":{"id":1}},{"id":11,"order":{"id":1}}]}""", writer.result())
    }

    @Test
    fun writeEntity_distinctInstancesWithTheSameKey_areBothWritten() {
        val order = Order(1L)
        val twin = Order(1L)
        twin.lines = mutableListOf(Line(20L))
        order.lines = mutableListOf(Line(10L, twin))

        val writer = JsonStreamWriter()
        OrderCodec.writeEntity(writer, order)

        assertEquals("""{"id":1,"lines":[{"id":10,"order":{"id":1,"lines":[{"id":20}]}}]}""", writer.result())
    }

    @Test
    fun readEntity_givesEachItemAStubBackReference() {
        val order = OrderCodec.readEntity(JsonStreamReader("""{"id":1,"unknown":true,"lines":[{"id":10},{"id":11}]}"""))

        assertEquals(1L, order.id)
        assertEquals(listOf<Long?>(10L, 11L), order.lines!!.map { it.id })
        for (line in order.lines!!) {
            assertEquals(1L, line.order!!.id)
            assertNotSame(order, line.order)
            assertNull(line.order!!.lines)
        }
    }

    @Test
    fun readEntityList_default_readsAnArray() {
        val list = OrderCodec.readEntityList(JsonStreamReader("""[{"id":1},{"id":2}]"""))
        assertEquals(listOf<Long?>(1L, 2L), list.map { it.id })
    }

    @Test
    fun updateData_carriesEntityAndProjection() {
        val entity = Order(1L)
        val projection = Order(1L)
        val data = ModelCodec.UpdateData(entity, projection)
        assertSame(entity, data.entity)
        assertSame(projection, data.projection)
    }
}
