package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScopeTest {

    init {
        JsonOutputFactory.installCommon()
    }

    private class TestScope : Scope() {
        var name: String? by field(null)
        var count: Int by field(0)
        var items: List<String> by field(emptyList())
        val tags = mutableListOf<String>()

    }

    @Test
    fun assigningADifferentValue_tellsTheUpdater_andTheSameValueDoesNot() {
        val scope = TestScope()
        var changes = 0
        scope.setUpdater { changes++ }

        scope.name = "a"
        scope.count = 1
        assertEquals(2, changes)

        scope.name = "a"
        scope.count = 1
        scope.items = emptyList()
        assertEquals(2, changes, "equal values are not a change")

        scope.items = listOf("x")
        scope.items = listOf("x")
        assertEquals(3, changes, "lists compare by content")
    }

    @Test
    fun aChangeInsideAnObject_needsChanged() {
        val scope = TestScope()
        var changes = 0
        scope.setUpdater { changes++ }

        scope.tags += "inside"
        assertEquals(0, changes, "the field cannot see a mutation inside the object")
        scope.changed()
        assertEquals(1, changes)
    }

    @Test
    fun aScopeWithNoUpdater_justKeepsTheValues() {
        val scope = TestScope()
        scope.count = 3
        scope.changed()
        assertEquals(3, scope.count)
    }

    @Test
    fun clearUpdater_takesBackOnlyTheUpdaterGiven() {
        val scope = TestScope()
        var old = 0
        var new = 0
        val oldUpdater: () -> Unit = { old++ }
        val newUpdater: () -> Unit = { new++ }

        scope.setUpdater(oldUpdater)
        scope.count = 1
        // another view starts drawing the scope; the old one stops afterwards
        scope.setUpdater(newUpdater)
        scope.clearUpdater(oldUpdater)
        scope.count = 2
        assertEquals(1, old)
        assertEquals(1, new, "the old view did not take the new view's updater")

        scope.clearUpdater(newUpdater)
        scope.count = 3
        assertEquals(1, new, "no one is told after the updater is taken back")
    }

    private class Holder : Scope() {
        var content: Scope? by field(null)
        var rows: List<TestScope> by scopes()
    }

    @Test
    fun aScopeInAField_goesAsTheIdOfItsView() {
        val ids = mutableMapOf<Scope, String>()
        val viewIdOf = { s: Scope -> ids.getOrPut(s) { "v:${ids.size + 1}" } }
        val holder = Holder().apply { content = TestScope(); rows = listOf(TestScope(), TestScope()) }
        assertEquals(
            """{"id":"h:1","contentViewId":"v:1","rowsViewIds":["v:2","v:3"]}""",
            holder.toJson("h:1", viewIdOf),
        )
        assertEquals("""{"id":"h:1","rowsViewIds":[]}""", Holder().toJson("h:1", viewIdOf), "an empty list still goes")
        assertEquals("""{"id":"h:1"}""", holder.toJson("h:1"), "without viewIdOf, the scopes are left out")
    }

    private enum class Color { RED }

    private class Point(val x: Int, val y: Int) : ScopeValue {
        override fun writeJson(json: ExtensibleObjectOutput) {
            json.beginObject().name("x").value(x.toLong()).name("y").value(y.toLong()).endObject()
        }
    }

    private class WireScope : Scope() {
        var title: String? by field("Cart")
        var blank: String? by field("  ")
        var missing: String? by field(null)
        var secret: String? by field("s3cret", sent = false)
        var count: Int by field(0)
        var price: Double by field(2.5)
        var open: Boolean by field(false)
        var color: Color by field(Color.RED)
        var points: List<Point> by field(listOf(Point(1, 2)))
        var tags: Map<String, Long> by field(mapOf("a" to 1L))
    }

    @Test
    fun write_sendsTheFieldsInOrder_byTheirRules() {
        assertEquals(
            """{"id":"v:1","title":"Cart","count":0,"price":2.5,"open":false,"color":"RED",""" +
                """"points":[{"x":1,"y":2}],"tags":{"a":1}}""",
            WireScope().toJson("v:1"),
        )
    }

    @Test
    fun write_ofAValueItCannotWrite_namesTheField() {
        class Odd : Scope() {
            var thing: Any? by field(Any())
        }
        val e = assertFailsWith<IllegalStateException> { Odd().toJson("v:1") }
        assertTrue("thing" in e.message!!, e.message)
    }
}
