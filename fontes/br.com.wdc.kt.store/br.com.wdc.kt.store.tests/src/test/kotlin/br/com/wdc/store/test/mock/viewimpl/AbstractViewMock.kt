package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.test.mock.RootPresenterMock
import java.util.concurrent.atomic.AtomicInteger

/** What the tests draw a scope with: it holds the scope, so a test reaches the scope's state and actions. */
abstract class AbstractViewMock<S : Scope>(
    val app: RootPresenterMock,
    val scope: S,
) {

    companion object {
        val INSTANCE_ID_GEN = AtomicInteger()
    }

    val instanceId: String = INSTANCE_ID_GEN.incrementAndGet().toString()

    /** How many times the scope asked for a repaint. */
    var updates = 0

    /** What the scope is told to call on a change ([Scope.setUpdater]). */
    val updater: () -> Unit = { updates++ }
}
