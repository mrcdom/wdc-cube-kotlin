package br.com.wdc.store.view.compose.bridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.presentation.presenter.RootPresenter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Cube views are independent: a view recomposes only when its own scope changes ([useScope]).
 *
 * Nothing in this module does that by hand: the Compose compiler (strong skipping) skips [RenderSlot] while it gets
 * the same scope, and remembers the lambdas created in a render, so a child that receives `{ scope.onX(id) }` is
 * skipped too. These tests pin that down, so a change that breaks it shows up. The scopes change as in the app: on
 * the presenters' dispatcher, painted by the scheduler's flush.
 */
@OptIn(ExperimentalTestApi::class)
class RecompositionTest {

    private class TestApp : RootPresenter() {
        override fun createSessionStorage(): SessionStorage = error("not used")
    }

    private class ChildScope : Scope() {
        var count: Int by field(0)
    }

    private class ParentScope : Scope() {
        var title: String by field("")
        var content: Scope? by field(null)
        var removed = -1L
        var onRemove: (Long) -> Unit = { removed = it }
    }

    private class ListScope : Scope() {
        var total: Int by field(0)
        var rows: List<ChildScope> by scopes()
    }

    /** How many times each view rendered, and the UI state each child remembered. */
    private class Renders {
        var parent = 0
        var button = 0
        var list = 0
        val child = mutableMapOf<ChildScope, Int>()
        val remembered = mutableMapOf<ChildScope, Any>()
        fun of(vararg scopes: ChildScope) = scopes.map { child[it] ?: 0 }
    }

    @Composable
    private fun ChildView(scope: ChildScope, renders: Renders) {
        useScope(scope)
        val uiState = remember { Any() }
        SideEffect {
            renders.child[scope] = (renders.child[scope] ?: 0) + 1
            renders.remembered[scope] = uiState
        }
        BasicText("child ${scope.count}")
    }

    @Composable
    private fun ParentView(scope: ParentScope, renders: Renders) {
        useScope(scope)
        SideEffect { renders.parent++ }
        BasicText("parent ${scope.title}")
        RenderSlot(scope.content)
        // A lambda created in the render, capturing the scope and an id
        Button(onClick = { scope.onRemove(42L) }, renders)
    }

    // It must use onClick: the compiler leaves unused parameters out of the skip check
    @Composable
    private fun Button(onClick: () -> Unit, renders: Renders) {
        SideEffect { renders.button++ }
        BasicText("remove", Modifier.clickable(onClick = onClick))
    }

    /** Same shape as CartView: each row is a view of its own, keyed by its scope where the list calls it. */
    @Composable
    private fun ListView(scope: ListScope, renders: Renders) {
        useScope(scope)
        SideEffect { renders.list++ }
        BasicText("list ${scope.total}")
        for (row in scope.rows) key(row) { RenderSlot(row) }
    }

    private val app = TestApp().also { app -> ViewUpdateScheduler.initialize { app } }
    private val renders = Renders()
    private val views = ScopeComposables()
        .register<ParentScope> { ParentView(it, renders) }
        .register<ChildScope> { ChildView(it, renders) }
        .register<ListScope> { ListView(it, renders) }

    @Composable
    private fun Show(scope: Scope?) {
        CompositionLocalProvider(LocalScopeComposables provides views) { RenderSlot(scope) }
    }

    /** Changes scopes as the presenters do — on their dispatcher — and paints, as the end of an action does. */
    private fun change(block: () -> Unit) = runBlocking {
        withContext(app.presenterScope.coroutineContext) {
            block()
            ViewUpdateScheduler.flush()
        }
    }

    @Test
    fun parentRecomposition_skipsTheSlotAndTheLambdaChild() = runComposeUiTest {
        val child = ChildScope()
        val parent = ParentScope().also { it.content = child }
        setContent { Show(parent) }
        waitForIdle()
        assertEquals(1, renders.parent); assertEquals(listOf(1), renders.of(child)); assertEquals(1, renders.button)

        change { parent.title = "changed" }
        waitForIdle()
        assertEquals(2, renders.parent, "parent")
        assertEquals(listOf(1), renders.of(child), "child in slot")
        assertEquals(1, renders.button, "child receiving a lambda")
    }

    @Test
    fun childRecomposition_doesNotRecomposeTheParent() = runComposeUiTest {
        val child = ChildScope()
        val parent = ParentScope().also { it.content = child }
        setContent { Show(parent) }
        waitForIdle()

        change { child.count = 1 }
        waitForIdle()
        assertEquals(1, renders.parent, "parent")
        assertEquals(listOf(2), renders.of(child), "child")
    }

    @Test
    fun anotherScopeInTheSlot_isANewView_withItsOwnUiState() = runComposeUiTest {
        val scopeA = ChildScope()
        val scopeB = ChildScope()
        val slot = mutableStateOf<Scope>(scopeA)
        setContent { Show(slot.value) }
        waitForIdle()
        runOnIdle { slot.value = scopeB }
        waitForIdle()
        assertEquals(listOf(1, 1), renders.of(scopeA, scopeB))
        assertNotSame(renders.remembered[scopeA], renders.remembered[scopeB], "remember starts over for another scope")
    }

    @Test
    fun aViewThatLeftTheScreen_isNotPaintedAgain() = runComposeUiTest {
        val child = ChildScope()
        val parent = ParentScope().also { it.content = child }
        setContent { Show(parent) }
        waitForIdle()

        change { parent.content = null }
        waitForIdle()
        change { child.count = 1 }
        waitForIdle()
        assertEquals(listOf(1), renders.of(child), "not painted after it left the composition")
    }

    @Test
    fun rows_recomposeOneByOne_andKeepTheirViewsWhenTheListChanges() = runComposeUiTest {
        val (a, b, c) = List(3) { ChildScope() }
        val list = ListScope().also { it.rows = listOf(a, b, c) }
        setContent { Show(list) }
        waitForIdle()
        assertEquals(1, renders.list)
        assertEquals(listOf(1, 1, 1), renders.of(a, b, c))

        // A row that changes repaints alone
        change { b.count = 1 }
        waitForIdle()
        assertEquals(1, renders.list, "list")
        assertEquals(listOf(1, 2, 1), renders.of(a, b, c))

        // The list repaints (the total changed): its rows are skipped
        change { list.total = 10 }
        waitForIdle()
        assertEquals(2, renders.list)
        assertEquals(listOf(1, 2, 1), renders.of(a, b, c))

        // A row leaves: the others keep their views and are not repainted
        val rememberedB = renders.remembered[b]
        change { list.rows = listOf(b, c) }
        waitForIdle()
        assertEquals(listOf(2, 1), renders.of(b, c))
        assertEquals(rememberedB, renders.remembered[b], "the row kept its view")
    }
}
