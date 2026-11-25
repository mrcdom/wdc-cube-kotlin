package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.gson.JsonExtensibleObjectOutput
import br.com.wdc.framework.cube.Scope
import com.google.gson.stream.JsonWriter
import java.io.StringWriter
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A remote view lets go of the views it no longer refers to when it is written again; no presenter takes part. */
class RemoteViewReleaseTest {

    private class PageScope : Scope() {
        var content: Scope? by field(null)
        var rows: List<RowScope> by scopes()
    }

    private class PanelScope : Scope() {
        var page: Int by field(0)
        var inner: Scope? by field(null)
    }

    private class RowScope : Scope() {
        var name: String? by field(null)
    }

    /** The session, with only what a view uses: the views it made, the dirty ones and the released ids. */
    private class FakeApp : RemoteViewHost {
        val views = IdentityHashMap<Scope, RemoteViewImpl<*>>()
        val dirty = LinkedHashSet<RemoteViewImpl<*>>()
        val released = ArrayList<String>()
        private var ids = 0

        override fun nextInstanceId(): Int = ++ids

        override fun viewOf(scope: Scope): RemoteViewImpl<*> = views.getOrPut(scope) {
            RemoteViewImpl(scope::class.simpleName!!, this, scope).also {
                scope.setUpdater(it.updater)
                markDirty(it)
            }
        }

        override fun forgetView(view: RemoteViewImpl<*>) {
            views.remove(view.scope, view)
            dirty.remove(view)
        }

        override fun removeView(instanceId: String) {
            released += instanceId
        }

        override fun markDirty(view: RemoteViewImpl<*>) {
            dirty += view
        }

        fun write(scope: Scope): RemoteViewImpl<*> =
            viewOf(scope).also { it.writeScope(JsonExtensibleObjectOutput(JsonWriter(StringWriter()))) }
    }

    @Test
    fun writingAScope_makesTheViewsOfTheScopesItHolds() {
        val app = FakeApp()
        val panel = PanelScope()
        val page = PageScope().apply { content = panel }

        app.write(page)

        assertTrue(panel in app.views)
        assertTrue(app.released.isEmpty())
    }

    @Test
    fun aScopeThatLeft_isReleasedWhenItsHolderIsWrittenAgain() {
        val app = FakeApp()
        val panel = PanelScope()
        val page = PageScope().apply { content = panel }
        app.write(page)
        val panelView = app.views.getValue(panel)

        page.content = PanelScope()
        app.write(page)

        assertTrue(panelView.released)
        assertEquals(listOf(panelView.instanceId), app.released)
        assertFalse(panel in app.views)
    }

    @Test
    fun aReleasedView_noLongerHearsItsScope() {
        val app = FakeApp()
        val panel = PanelScope()
        val page = PageScope().apply { content = panel }
        app.write(page)
        val panelView = app.views.getValue(panel)
        page.content = null
        app.write(page)
        app.dirty.clear()

        panel.page = 2

        assertFalse(panelView in app.dirty)
    }

    @Test
    fun theViewsAReleasedViewHeld_goWithIt() {
        val app = FakeApp()
        val inner = RowScope()
        val panel = PanelScope().apply { this.inner = inner }
        val page = PageScope().apply { content = panel }
        app.write(page)
        app.write(panel)
        val innerView = app.views.getValue(inner)

        page.content = null
        app.write(page)

        assertTrue(innerView.released)
        assertEquals(2, app.released.size)
    }

    @Test
    fun aRowThatLeftTheList_isReleased_andTheOthersStay() {
        val app = FakeApp()
        val first = RowScope()
        val second = RowScope()
        val page = PageScope().apply { rows = listOf(first, second) }
        app.write(page)
        val firstView = app.views.getValue(first)
        val secondView = app.views.getValue(second)

        page.rows = listOf(second)
        app.write(page)

        assertTrue(firstView.released)
        assertFalse(secondView.released)
        assertSame(secondView, app.views[second])
    }

    @Test
    fun aViewHeldByTwoViews_staysUntilBothLetGo() {
        val app = FakeApp()
        val shared = RowScope()
        val a = PanelScope().apply { inner = shared }
        val b = PanelScope().apply { inner = shared }
        app.write(a)
        app.write(b)
        val sharedView = app.views.getValue(shared)

        a.inner = null
        app.write(a)
        assertFalse(sharedView.released)

        b.inner = null
        app.write(b)
        assertTrue(sharedView.released)
    }

    @Test
    fun aScopeThatComesBack_getsANewView() {
        val app = FakeApp()
        val panel = PanelScope()
        val page = PageScope().apply { content = panel }
        app.write(page)
        val before = app.views.getValue(panel)
        page.content = null
        app.write(page)

        page.content = panel
        app.write(page)

        assertNotSame(before, app.views[panel])
        assertFalse(app.views.getValue(panel).released)
    }
}
