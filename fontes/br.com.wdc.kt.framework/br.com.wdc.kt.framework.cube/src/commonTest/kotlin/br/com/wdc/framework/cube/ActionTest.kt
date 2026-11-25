package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.log.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ActionTest {

    private class App : CubeApplication() {
        var histories = 0
        val alerts = mutableListOf<String>()

        override fun updateHistory() {
            histories++
        }

        override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
            alerts += "$message: ${e.message}"
        }
    }

    private class ShopScope : Scope() {
        var count: Int by field(0)
        var onAdd: Action = Action.NOOP
        var onSet: ActionOf<Int?> = ActionOf.noop()
        var onMove: ActionOf2<Long?, Int?> = ActionOf2.noop()
        var onFail: Action = Action.NOOP
    }

    private class ShopPresenter(app: App) : AbstractCubePresenter<App>(app) {
        val scope = bind(ShopScope())
        val moves = mutableListOf<Pair<Long?, Int?>>()

        init {
            scope.onAdd = action(1) { scope.count++ }
            scope.onSet = actionOf(2, FormParam.int("p.count")) { scope.count = it ?: -1 }
            scope.onMove = actionOf2(3, FormParam.long("p.id"), FormParam.int("p.to")) { id, to -> moves += id to to }
            scope.onFail = action { throw IllegalStateException("boom") }
        }
    }

    @Test
    fun anAction_runsItsBody_thenRepublishesTheAddress() = runTest {
        val app = App()
        val presenter = ShopPresenter(app)
        presenter.scope.onAdd()
        presenter.scope.onSet(7)
        assertEquals(7, presenter.scope.count)
        assertEquals(2, app.histories)
    }

    @Test
    fun aFailureTheBodyDidNotHandle_goesToTheApplication_andTheAddressIsStillRepublished() = runTest {
        val app = App()
        val presenter = ShopPresenter(app)
        presenter.scope.onFail()
        assertEquals(listOf("Unexpected error: boom"), app.alerts)
        assertEquals(1, app.histories)
    }

    @Test
    fun aRemoteEvent_runsTheActionBoundToItsCode_withTheValuesOfTheForm() = runTest {
        val presenter = ShopPresenter(App())
        presenter.scope.dispatch(1, emptyMap())
        assertEquals(1, presenter.scope.count)
        presenter.scope.dispatch(2, mapOf("p.count" to "12"))
        assertEquals(12, presenter.scope.count)
        presenter.scope.dispatch(3, mapOf("p.id" to 5, "p.to" to 2.0))
        assertEquals(listOf<Pair<Long?, Int?>>(5L to 2), presenter.moves)
    }

    @Test
    fun anUnknownCode_isAnError_notIgnored() = runTest {
        val presenter = ShopPresenter(App())
        val e = assertFailsWith<IllegalArgumentException> { presenter.scope.dispatch(9, emptyMap()) }
        assertTrue("9" in e.message!!, e.message)
    }

    @Test
    fun aCodeBoundTwice_failsWhenBinding() {
        class Twice(app: App) : AbstractCubePresenter<App>(app) {
            val scope = bind(ShopScope())

            init {
                scope.onAdd = action(1) {}
                scope.onFail = action(1) {}
            }
        }
        assertFailsWith<IllegalStateException> { Twice(App()) }
    }

    @Test
    fun sync_copiesWhatTheViewSent_beforeTheEvent() = runTest {
        val presenter = ShopPresenter(App())
        presenter.scope.onSync { presenter.scope.count = (it["count"] as String).toInt() }
        presenter.scope.sync(mapOf("count" to "4"))
        assertEquals(4, presenter.scope.count)
    }

    private class SlowPresenter(app: App) : AbstractCubePresenter<App>(app) {
        val scope = bind(ShopScope())
        val log = mutableListOf<String>()
        val network = CompletableDeferred<Unit>()

        init {
            // waits on "the network" between its two steps
            scope.onAdd = action {
                log += "buy:start"
                network.await()
                log += "buy:end"
            }
            scope.onSet = actionOf { log += "set:$it" }
            // an action that runs another one of the same application
            scope.onMove = actionOf2 { _, _ ->
                log += "move"
                scope.onSet(1)
            }
        }
    }

    @Test
    fun anActionThatStartsWhileAnotherWaitsOnIO_runsAfterIt_notInterleaved() = runTest {
        val presenter = SlowPresenter(App())
        launch { presenter.scope.onAdd() }
        runCurrent()
        launch { presenter.scope.onSet(2) }
        runCurrent()
        assertEquals(listOf("buy:start"), presenter.log, "the second action waits")

        presenter.network.complete(Unit)
        runCurrent()
        assertEquals(listOf("buy:start", "buy:end", "set:2"), presenter.log)
    }

    @Test
    fun anActionThatRunsAnother_doesNotWaitForItself() = runTest {
        val presenter = SlowPresenter(App())
        presenter.scope.onMove(1L, 2)
        assertEquals(listOf("move", "set:1"), presenter.log)
    }
}
