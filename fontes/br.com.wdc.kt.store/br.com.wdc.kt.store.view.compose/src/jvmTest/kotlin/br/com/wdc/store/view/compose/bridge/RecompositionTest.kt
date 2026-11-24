package br.com.wdc.store.view.compose.bridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.store.presentation.presenter.RootPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Cube views are independent: a view recomposes only when its own scope changes ([ComposeCubeView.revision]).
 *
 * Unlike the native web view, nothing in this module does that by hand: the Compose compiler (strong skipping)
 * skips [RenderSlot] when it gets the same view — [RootPresenter.viewOf][br.com.wdc.framework.cube.CubeApplication.viewOf]
 * gives the same instance for the same scope — and remembers the lambdas created in a render, so a child that
 * receives `{ scope.onX(id) }` is skipped too. These tests pin that down, so a change that breaks it shows up.
 */
@OptIn(ExperimentalTestApi::class)
class RecompositionTest {

    private class TestApp : RootPresenter() {
        override fun createSessionStorage(): SessionStorage = error("not used")
    }

    private class ChildScope : Scope()

    private class ParentScope : Scope() {
        var content: Scope? by field(null)
        var removed = -1L
        var onRemove: (Long) -> Unit = { removed = it }
    }

    private class ChildView(app: RootPresenter, scope: ChildScope, id: String) :
        ComposeCubeView<ChildScope>(id, app, scope) {
        var renders = 0
        @Composable
        override fun Render() {
            val rev = revision.value
            SideEffect { renders++ }
            BasicText("$instanceId $rev")
        }
    }

    private class ParentView(app: RootPresenter, scope: ParentScope) :
        ComposeCubeView<ParentScope>("parent", app, scope) {
        var renders = 0
        var buttonRenders = 0
        private val itemId = 42L

        @Composable
        override fun Render() {
            val rev = revision.value
            SideEffect { renders++ }
            BasicText("parent $rev")
            scope.content?.let(app::viewOf)?.let { RenderSlot(it) }
            // A lambda created in the render, capturing the scope and an id
            Button(onClick = { scope.onRemove(itemId) })
        }

        // It must use onClick: the compiler leaves unused parameters out of the skip check
        @Composable
        private fun Button(onClick: () -> Unit) {
            SideEffect { buttonRenders++ }
            BasicText("remove", Modifier.clickable(onClick = onClick))
        }
    }

    private class ListScope : Scope() {
        var rows: List<ChildScope> by scopes()
    }

    /** Same shape as CartView: each row is a view of its own, keyed by its scope. */
    private class ListView(app: RootPresenter, scope: ListScope) :
        ComposeCubeView<ListScope>("list", app, scope) {
        var renders = 0

        @Composable
        override fun Render() {
            val rev = revision.value
            SideEffect { renders++ }
            BasicText("list $rev")
            for (row in scope.rows) {
                key(row) { RenderSlot(app.viewOf(row)) }
            }
        }
    }

    /** An application whose catalog draws [ParentScope] and [ChildScope]; the parent shows [child]. */
    private class Fixture(child: ChildScope = ChildScope()) {
        val app = TestApp()
        val parentScope = ParentScope().also { it.content = child }

        init {
            app.scopeViews = ScopeViews()
                .register<ParentScope> { ParentView(app, it) }
                .register<ChildScope> { ChildView(app, it, "child-${it.hashCode()}") }
                .register<ListScope> { ListView(app, it) }
        }

        val parent get() = app.viewOf(parentScope) as ParentView
        fun viewOf(scope: ChildScope) = app.viewOf(scope) as ChildView
        val child get() = viewOf(parentScope.content as ChildScope)
    }

    @Test
    fun parentRecomposition_skipsTheSlotAndTheLambdaChild() = runComposeUiTest {
        val f = Fixture()
        val parent = f.parent
        setContent { parent.Render() }
        waitForIdle()
        val child = f.child
        assertSame(child, f.child, "viewOf gives the same view for the same scope")
        assertEquals(1, parent.renders); assertEquals(1, child.renders); assertEquals(1, parent.buttonRenders)

        runOnIdle { parent.revision.value++ }
        waitForIdle()
        assertEquals(2, parent.renders, "parent")
        assertEquals(1, child.renders, "child in slot")
        assertEquals(1, parent.buttonRenders, "child receiving a lambda")
    }

    @Test
    fun childRecomposition_doesNotRecomposeTheParent() = runComposeUiTest {
        val f = Fixture()
        val parent = f.parent
        setContent { parent.Render() }
        waitForIdle()
        val child = f.child

        runOnIdle { child.revision.value++ }
        waitForIdle()
        assertEquals(1, parent.renders, "parent")
        assertEquals(2, child.renders, "child")
    }

    @Test
    fun differentViewInTheSlot_recomposesIt() = runComposeUiTest {
        val scopeA = ChildScope()
        val scopeB = ChildScope()
        val f = Fixture(scopeA)
        val slot = mutableStateOf<Scope>(scopeA)
        setContent { RenderSlot(f.app.viewOf(slot.value)) }
        waitForIdle()
        runOnIdle { slot.value = scopeB }
        waitForIdle()
        assertEquals(1, f.viewOf(scopeA).renders); assertEquals(1, f.viewOf(scopeB).renders)
    }

    @Test
    fun rows_recomposeOneByOne_andKeepTheirViewsWhenTheListChanges() = runComposeUiTest {
        val f = Fixture()
        val (a, b, c) = List(3) { ChildScope() }
        val listScope = ListScope().also { it.rows = listOf(a, b, c) }
        val list = f.app.viewOf(listScope) as ListView
        setContent { list.Render() }
        waitForIdle()
        assertEquals(1, list.renders)
        assertEquals(listOf(1, 1, 1), listOf(a, b, c).map { f.viewOf(it).renders })

        // A row that changes repaints alone
        runOnIdle { f.viewOf(b).revision.value++ }
        waitForIdle()
        assertEquals(1, list.renders, "list")
        assertEquals(listOf(1, 2, 1), listOf(a, b, c).map { f.viewOf(it).renders })

        // The list repaints (the total changed): its rows are skipped
        runOnIdle { list.revision.value++ }
        waitForIdle()
        assertEquals(listOf(1, 2, 1), listOf(a, b, c).map { f.viewOf(it).renders })

        // A row leaves: the others keep their views and are not repainted
        runOnIdle { listScope.rows = listOf(b, c); list.revision.value++ }
        waitForIdle()
        assertEquals(listOf(2, 1), listOf(b, c).map { f.viewOf(it).renders })
    }
}
