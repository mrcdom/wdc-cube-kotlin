package br.com.wdc.shopping.nativeui.web.bridge

import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.PresenterBase
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import react.FC
import react.Props
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import react.dom.html.ReactHTML.div
import react.useEffect
import react.useState
import web.dom.Element

/**
 * A slot repaints only when the parent puts a different view in it; a view repaints itself when its own state
 * changes. Cube views are independent: a parent repaint is no reason to repaint the views in its slots.
 */
class RenderSlotTest {

    private class TestApp : CubeApplication()

    private class TestPresenter : PresenterBase {
        override val app: CubeApplication = TestApp()
        override fun release() {}
    }

    /** A view that counts its renders and subscribes to its own updates, as every view in this module does. */
    private class CountingView(id: String) : ReactCubeView(id, TestPresenter()) {
        var renders = 0

        override val component = FC<Props> {
            var rev by useState(revision)
            useEffect(this@CountingView) {
                onUpdate = { rev = revision }
            }
            renders++
            div { +"$id rev $rev" }
        }
    }

    /** A view whose UI state lives in a field, changed by an emitter that calls [forceUpdate]. */
    private class CounterView : ReactCubeView("counter", TestPresenter()) {
        var renders = 0
        private var count = 0
        val increment: () -> Unit = {
            count++
            forceUpdate()
        }

        override val component = FC<Props> {
            var rev by useState(revision)
            useEffect(this@CounterView) {
                onUpdate = { rev = revision }
            }
            renders++
            div { +"count $count" }
        }
    }

    private var root: Root? = null

    @AfterTest
    fun unmount() {
        root?.unmount()
        root = null
    }

    /** Lets React commit the pending renders and run their effects. */
    private suspend fun settle() = Promise<Unit> { resolve, _ -> window.setTimeout({ resolve(Unit) }, 30) }.await()

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun parentRepaint_leavesTheSlot_andTheViewRepaintsItself() = GlobalScope.promise {
        val first = CountingView("first")
        val second = CountingView("second")
        var parentRenders = 0
        var bump: (() -> Unit)? = null
        var swap: (() -> Unit)? = null

        val parent = FC<Props> {
            var counter by useState(0)
            var useSecond by useState(false)
            bump = { counter++ }
            swap = { useSecond = !useSecond }
            parentRenders++
            div {
                +"parent $counter"
                RenderSlot { view = if (useSecond) second else first }
            }
        }

        val container = document.createElement("div")
        document.body!!.appendChild(container)
        root = createRoot(container.unsafeCast<Element>()).also { it.render(parent.create()) }
        settle()
        assertEquals(1, parentRenders)
        assertEquals(1, first.renders)

        // the parent repaints (its own state changed): the view in the slot does not
        bump!!()
        settle()
        assertEquals(2, parentRenders)
        assertEquals(1, first.renders, "a parent repaint must not repaint the slot")

        // the view's own state changed: it repaints, the parent does not
        first.notifyDirty()
        settle()
        assertEquals(2, first.renders)
        assertEquals(2, parentRenders)
        assertEquals("first rev 1", container.textContent?.substringAfter("parent 1"))

        // the parent puts another view in the slot: the slot repaints with it
        swap!!()
        settle()
        assertEquals(1, second.renders)
        assertEquals("second rev 0", container.textContent?.substringAfter("parent 1"))
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun forceUpdate_repaintsTheViewItself_withoutTheParent() = GlobalScope.promise {
        val view = CounterView()
        var parentRenders = 0
        val parent = FC<Props> {
            parentRenders++
            RenderSlot { this.view = view }
        }
        val container = document.createElement("div")
        document.body!!.appendChild(container)
        root = createRoot(container.unsafeCast<Element>()).also { it.render(parent.create()) }
        settle()
        assertEquals("count 0", container.textContent)

        view.increment()
        settle()
        assertEquals("count 1", container.textContent)
        assertEquals(2, view.renders)
        assertEquals(1, parentRenders, "the parent is not repainted")
    }
}
