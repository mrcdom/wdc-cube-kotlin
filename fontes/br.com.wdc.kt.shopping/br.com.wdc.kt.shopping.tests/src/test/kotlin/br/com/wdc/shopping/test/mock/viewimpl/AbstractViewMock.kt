package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.PresenterBase
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.test.mock.RootPresenterMock
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractViewMock<P : PresenterBase>(
    val app: RootPresenterMock,
    val presenter: P,
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
