package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelViewState
import org.junit.jupiter.api.Assertions

class ProductsPanelViewMock(
    val presenter: ProductsPanelPresenter,
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

    var state: ProductsPanelViewState = presenter.state

    override fun release() {
        // NOOP
    }

    override fun update() {
        // NOOP
    }
}
