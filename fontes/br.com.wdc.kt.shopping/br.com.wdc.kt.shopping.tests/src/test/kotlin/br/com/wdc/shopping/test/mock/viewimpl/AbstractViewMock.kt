package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractViewMock<P : CubePresenter>(
    val app: ShoppingApplicationMock,
    val presenter: P,
) : CubeView {

    companion object {
        val INSTANCE_ID_GEN = AtomicInteger()
    }

    var released = false

    override fun release() {
        released = true
    }

    override fun update() {
        // NOOP
    }

    override fun instanceId(): String {
        return INSTANCE_ID_GEN.incrementAndGet().toString()
    }
}
