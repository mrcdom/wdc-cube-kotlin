package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelViewState
import org.junit.jupiter.api.Assertions

class PurchasesPanelViewMock(
    val presenter: PurchasesPanelPresenter,
) : CubeView {

    companion object {
        fun cast(view: CubeView?): PurchasesPanelViewMock {
            Assertions.assertNotNull(view, "Expecting PurchasesPanelViewMock but this view was null")
            Assertions.assertInstanceOf(PurchasesPanelViewMock::class.java, view,
                "Expecting PurchasesPanelViewMock but it was ${view!!::class.simpleName}")
            return view as PurchasesPanelViewMock
        }
    }

    var state: PurchasesPanelViewState = presenter.state

    override fun release() {
        // NOOP
    }

    override fun update() {
        // NOOP
    }

    override fun instanceId(): String {
        return AbstractViewMock.INSTANCE_ID_GEN.incrementAndGet().toString()
    }
}
