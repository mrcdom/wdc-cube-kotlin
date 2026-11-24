package br.com.wdc.shopping.view.compose.bridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.PresenterBase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cube views are independent: a view recomposes only when its own scope changes ([ComposeCubeView.revision]).
 *
 * Unlike the native web view, nothing in this module does that by hand: the Compose compiler (strong skipping)
 * skips [RenderSlot] when it gets the same view, and remembers the lambdas created in a render, so a child that
 * receives `{ presenter.onX(id) }` is skipped too. These tests pin that down, so a change that breaks it shows up.
 */
@OptIn(ExperimentalTestApi::class)
class RecompositionTest {

    private class TestApp : CubeApplication()

    private class TestPresenter : PresenterBase {
        override val app: CubeApplication = TestApp()
        var removed = -1L
        fun onRemove(id: Long) { removed = id }
        override fun release() {}
    }

    private class ChildView(id: String) : ComposeCubeView(id, TestPresenter()) {
        var renders = 0
        @Composable
        override fun Render() {
            val rev = revision.value
            SideEffect { renders++ }
            BasicText("$instanceId $rev")
        }
    }

    private class ParentView(val child: ChildView) : ComposeCubeView("parent", TestPresenter()) {
        val presenter = presenterBase as TestPresenter
        var renders = 0
        var buttonRenders = 0
        private val itemId = 42L

        @Composable
        override fun Render() {
            val rev = revision.value
            SideEffect { renders++ }
            BasicText("parent $rev")
            RenderSlot(child)
            // Same shape as CartView: a lambda created in the render, capturing the presenter and an item id
            Button(onClick = { presenter.onRemove(itemId) })
        }

        // It must use onClick: the compiler leaves unused parameters out of the skip check
        @Composable
        private fun Button(onClick: () -> Unit) {
            SideEffect { buttonRenders++ }
            BasicText("remove", Modifier.clickable(onClick = onClick))
        }
    }

    @Test
    fun parentRecomposition_skipsTheSlotAndTheLambdaChild() = runComposeUiTest {
        val child = ChildView("child")
        val parent = ParentView(child)
        setContent { parent.Render() }
        waitForIdle()
        assertEquals(1, parent.renders); assertEquals(1, child.renders); assertEquals(1, parent.buttonRenders)

        runOnIdle { parent.revision.value++ }
        waitForIdle()
        assertEquals(2, parent.renders, "parent")
        assertEquals(1, child.renders, "child in slot")
        assertEquals(1, parent.buttonRenders, "child receiving a lambda")
    }

    @Test
    fun childRecomposition_doesNotRecomposeTheParent() = runComposeUiTest {
        val child = ChildView("child")
        val parent = ParentView(child)
        setContent { parent.Render() }
        waitForIdle()

        runOnIdle { child.revision.value++ }
        waitForIdle()
        assertEquals(1, parent.renders, "parent")
        assertEquals(2, child.renders, "child")
    }

    @Test
    fun differentViewInTheSlot_recomposesIt() = runComposeUiTest {
        val a = ChildView("a")
        val b = ChildView("b")
        val slot = mutableStateOf<ComposeCubeView>(a)
        setContent { RenderSlot(slot.value) }
        waitForIdle()
        runOnIdle { slot.value = b }
        waitForIdle()
        assertEquals(1, a.renders); assertEquals(1, b.renders)
    }
}
