package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.store.test.mock.RootPresenterMock
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractViewMock<S : Scope>(
    val app: RootPresenterMock,
    val scope: S,
) : CubeView {

    companion object {
        val INSTANCE_ID_GEN = AtomicInteger()
    }

    override val instanceId: String = INSTANCE_ID_GEN.incrementAndGet().toString()

    var released = false

    override fun release() {
        released = true
    }

    /** How many times the presenter asked for a repaint. */
    var updates = 0

    override fun update() {
        updates++
    }
}
