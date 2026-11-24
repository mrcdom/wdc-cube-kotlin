package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import org.junit.jupiter.api.Assertions

class ProductsPanelViewMock(
    val scope: ProductsPanelScope,
) : CubeView {

    companion object {
        fun cast(view: CubeView?): ProductsPanelViewMock {
            Assertions.assertNotNull(view, "Expecting ProductsPanelViewMock but this view was null")
            Assertions.assertInstanceOf(ProductsPanelViewMock::class.java, view,
                "Expecting ProductsPanelViewMock but it was ${view!!::class.simpleName}")
            return view as ProductsPanelViewMock
        }
    }

    override val instanceId: String = AbstractViewMock.INSTANCE_ID_GEN.incrementAndGet().toString()


    override fun release() {
        // NOOP
    }

    override fun update() {
        // NOOP
    }
}
