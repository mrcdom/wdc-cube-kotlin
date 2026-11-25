package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions

class ProductsPanelViewMock(app: RootPresenterMock, scope: ProductsPanelScope) : AbstractViewMock<ProductsPanelScope>(app, scope) {

    companion object {
        fun cast(view: AbstractViewMock<*>?): ProductsPanelViewMock {
            Assertions.assertNotNull(view, "Expecting ProductsPanelViewMock but this view was null")
            Assertions.assertInstanceOf(ProductsPanelViewMock::class.java, view,
                "Expecting ProductsPanelViewMock but it was ${view!!::class.simpleName}")
            return view as ProductsPanelViewMock
        }
    }
}
