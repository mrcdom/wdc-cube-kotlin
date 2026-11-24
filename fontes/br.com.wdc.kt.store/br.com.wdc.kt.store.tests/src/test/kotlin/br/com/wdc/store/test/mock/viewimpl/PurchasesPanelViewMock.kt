package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import org.junit.jupiter.api.Assertions

class PurchasesPanelViewMock(
    val scope: PurchasesPanelScope,
) : CubeView {

    companion object {
        fun cast(view: CubeView?): PurchasesPanelViewMock {
            Assertions.assertNotNull(view, "Expecting PurchasesPanelViewMock but this view was null")
            Assertions.assertInstanceOf(PurchasesPanelViewMock::class.java, view,
                "Expecting PurchasesPanelViewMock but it was ${view!!::class.simpleName}")
            return view as PurchasesPanelViewMock
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
