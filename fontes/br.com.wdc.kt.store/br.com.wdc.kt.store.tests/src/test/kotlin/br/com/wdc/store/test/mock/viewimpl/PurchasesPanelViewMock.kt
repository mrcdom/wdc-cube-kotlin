package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions

class PurchasesPanelViewMock(app: RootPresenterMock, scope: PurchasesPanelScope) : AbstractViewMock<PurchasesPanelScope>(app, scope) {

    companion object {
        fun cast(view: AbstractViewMock<*>?): PurchasesPanelViewMock {
            Assertions.assertNotNull(view, "Expecting PurchasesPanelViewMock but this view was null")
            Assertions.assertInstanceOf(PurchasesPanelViewMock::class.java, view,
                "Expecting PurchasesPanelViewMock but it was ${view!!::class.simpleName}")
            return view as PurchasesPanelViewMock
        }
    }
}
