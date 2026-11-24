package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    private class TestView : CubeView {
        var updates = 0
        var onRelease: () -> Unit = {}
        override val instanceId: String = "test"
        override fun update() {
            updates++
        }
        override fun release() = onRelease()
    }

    private class TestPresenter(app: CubeApplication) : AbstractCubePresenter<CubeApplication>(app) {
        val scope = bind(TestScope())
    }

    @Test
    fun assigningADifferentValue_notifies_andTheSameValueDoesNot() {
        val scope = TestScope()
        var changes = 0
        scope.subscribe { changes++ }

        scope.name = "a"
        scope.count = 1
        assertEquals(2, changes)
        assertEquals(2, scope.version)

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
        scope.subscribe { changes++ }

        scope.tags += "inside"
        assertEquals(0, changes, "the field cannot see a mutation inside the object")
        scope.changed()
        assertEquals(1, changes)
    }

    @Test
    fun manySubscribers_eachUntilItUnsubscribes() {
        val scope = TestScope()
        var first = 0
        var second = 0
        val unsubscribeFirst = scope.subscribe { first++ }
        scope.subscribe { second++ }

        scope.count = 1
        unsubscribeFirst()
        unsubscribeFirst()
        scope.count = 2
        assertEquals(1, first)
        assertEquals(2, second)
    }

    @Test
    fun release_tellsOnce_andDropsTheSubscribers() {
        val scope = TestScope()
        var changes = 0
        var releases = 0
        var cancelled = 0
        scope.subscribe { changes++ }
        scope.onRelease { releases++ }
        val cancel = scope.onRelease { cancelled++ }
        cancel()

        scope.release()
        scope.release()
        scope.count = 1
        assertTrue(scope.released)
        assertEquals(1, releases)
        assertEquals(0, cancelled, "removed before the release")
        assertEquals(0, changes, "no subscriber after the release")
    }

    @Test
    fun theLayersViews_repaintWithTheScope_andAreReleasedWithIt() {
        val app = object : CubeApplication() {}
        val view = TestView()
        var released = 0
        val views = ScopeViews().register<TestScope> { view.also { it.onRelease = { released++ } } }
        val presenter = TestPresenter(app)

        presenter.scope.name = "before the view"
        assertSame(view, views.viewOf(presenter.scope), "the catalog's view, created once")
        assertSame(view, views.viewOf(presenter.scope))
        presenter.scope.name = "with the view"
        presenter.scope.count = 7
        assertEquals(2, view.updates)

        presenter.release()
        assertTrue(presenter.scope.released, "the presenter releases its scope")
        assertNull(views.viewOrNull(presenter.scope), "dropped with its scope")
        assertEquals(1, released)
        presenter.scope.count = 8
        assertEquals(2, view.updates)
    }

    private class Holder : Scope() {
        var content: Scope? by field(null)
        var rows: List<TestScope> by scopes()
    }

    @Test
    fun aScopeInAField_goesAsTheIdOfItsView() {
        val views = ScopeViews().register<TestScope> { TestView() }.fallback { s -> TestView().takeIf { s is Holder } }
        val holder = Holder().apply { content = TestScope(); rows = listOf(TestScope(), TestScope()) }
        assertEquals(
            """{"id":"h:1","contentViewId":"test","rowsViewIds":["test","test"]}""",
            holder.toJson("h:1", views::viewOf),
        )
        assertEquals("""{"id":"h:1","rowsViewIds":[]}""", Holder().toJson("h:1", views::viewOf), "an empty list still goes")
    }

    @Test
    fun aScopeWithNoViewInTheCatalog_failsNamingIt() {
        val e = assertFailsWith<IllegalStateException> { ScopeViews().viewOf(TestScope()) }
        assertTrue("TestScope" in e.message!!, e.message)
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
        var content: CubeView? by field(TestView())
        var points: List<Point> by field(listOf(Point(1, 2)))
        var tags: Map<String, Long> by field(mapOf("a" to 1L))
    }

    @Test
    fun write_sendsTheFieldsInOrder_byTheirRules() {
        assertEquals(
            """{"id":"v:1","title":"Cart","count":0,"price":2.5,"open":false,"color":"RED",""" +
                """"contentId":"test","points":[{"x":1,"y":2}],"tags":{"a":1}}""",
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

    private class OwnerPresenter(app: CubeApplication) : AbstractCubePresenter<CubeApplication>(app) {
        val scope = bind(Holder())
        val panel = segment(TestScope())
    }

    @Test
    fun aSegment_hasItsOwnView_andIsReleasedWithThePresenter() {
        val app = object : CubeApplication() {}
        val ownerView = TestView()
        val panelView = TestView()
        val views = ScopeViews().register<Holder> { ownerView }.register<TestScope> { panelView }
        val presenter = OwnerPresenter(app)
        views.viewOf(presenter.scope)
        views.viewOf(presenter.panel)

        presenter.panel.name = "changed"
        assertEquals(1, panelView.updates)
        assertEquals(0, ownerView.updates, "the owner does not repaint for its segment")

        presenter.release()
        assertTrue(presenter.panel.released)
        assertNull(views.viewOrNull(presenter.panel))
        assertEquals(0, views.size)
    }
}
