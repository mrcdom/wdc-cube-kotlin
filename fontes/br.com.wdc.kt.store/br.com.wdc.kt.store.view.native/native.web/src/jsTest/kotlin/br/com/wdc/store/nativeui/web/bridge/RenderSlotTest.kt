package br.com.wdc.store.nativeui.web.bridge

import br.com.wdc.framework.cube.Scope
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
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.useState
import web.dom.Element
import web.dom.ElementId
import web.html.HTMLButtonElement

/**
 * A slot repaints only when the parent puts a different scope in it; a view repaints itself when its own scope
 * changes. Cube views are independent: a parent repaint is no reason to repaint the views in its slots. A view that
 * left the screen is not painted again: it took its updater back from the scope when it unmounted.
 */
class RenderSlotTest {

    private class TestScope(val name: String) : Scope() {
        var count: Int by field(0)
    }

    /** How many times the view of each scope rendered. */
    private val renders = mutableMapOf<String, Int>()

    /** A view that counts its renders and keeps UI state of its own (a counter the user moves). */
    private val TestView = FC<ScopeProps<TestScope>> { props ->
        val scope = useScope(props.scope)
        var clicks by useState(0)
        renders[scope.name] = (renders[scope.name] ?: 0) + 1
        div { +"${scope.name} ${scope.count} clicks $clicks" }
        button {
            id = ElementId("click-${scope.name}")
            onClick = { clicks++ }
        }
    }

    private val views = ScopeComponents().register(TestScope::class, TestView)

    private var root: Root? = null

    @AfterTest
    fun unmount() {
        root?.unmount()
        root = null
    }

    /** Lets React commit the pending renders and run their effects. */
    private suspend fun settle() = Promise<Unit> { resolve, _ -> window.setTimeout({ resolve(Unit) }, 30) }.await()

    private fun mount(parent: FC<Props>): Element {
        val container = document.createElement("div")
        document.body!!.appendChild(container)
        val app = FC<Props> {
            ScopeComponentsContext.Provider {
                value = views
                parent()
            }
        }
        root = createRoot(container.unsafeCast<Element>()).also { it.render(app.create()) }
        return container.unsafeCast<Element>()
    }

    /** Changes a scope and paints, as the end of an action does. */
    private fun change(block: () -> Unit) {
        block()
        ViewUpdateScheduler.flush()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun parentRepaint_leavesTheSlot_andTheViewRepaintsItself() = GlobalScope.promise {
        val first = TestScope("first")
        val second = TestScope("second")
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
                RenderSlot { scope = if (useSecond) second else first }
            }
        }

        val container = mount(parent)
        settle()
        assertEquals(1, parentRenders)
        assertEquals(1, renders["first"])

        // the parent repaints (its own state changed): the view in the slot does not
        bump!!()
        settle()
        assertEquals(2, parentRenders)
        assertEquals(1, renders["first"], "a parent repaint must not repaint the slot")

        // the view's own scope changed: it repaints, the parent does not
        change { first.count = 1 }
        settle()
        assertEquals(2, renders["first"])
        assertEquals(2, parentRenders)
        assertEquals("first 1 clicks 0", container.textContent?.substringAfter("parent 1"))

        // the parent puts another scope in the slot: the slot repaints with it
        swap!!()
        settle()
        assertEquals(1, renders["second"])
        assertEquals("second 0 clicks 0", container.textContent?.substringAfter("parent 1"))

        // the first one left the screen: a change in it paints nothing
        change { first.count = 2 }
        settle()
        assertEquals(2, renders["first"], "not painted after it left the screen")
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun uiStateOfTheView_repaintsItAlone_andStartsOverForAnotherScope() = GlobalScope.promise {
        val first = TestScope("first")
        val second = TestScope("second")
        var parentRenders = 0
        var swap: (() -> Unit)? = null
        val parent = FC<Props> {
            var useSecond by useState(false)
            swap = { useSecond = !useSecond }
            parentRenders++
            RenderSlot { scope = if (useSecond) second else first }
        }
        val container = mount(parent)
        settle()
        assertEquals("first 0 clicks 0", container.textContent)

        document.getElementById("click-first").unsafeCast<HTMLButtonElement>().click()
        settle()
        assertEquals("first 0 clicks 1", container.textContent)
        assertEquals(2, renders["first"])
        assertEquals(1, parentRenders, "the parent is not repainted")

        swap!!()
        settle()
        assertEquals("second 0 clicks 0", container.textContent, "another scope is another view")
    }
}
