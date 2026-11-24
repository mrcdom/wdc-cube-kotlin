package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions

class ProductViewMock(
    app: RootPresenterMock,
    scope: ProductScope,
) : AbstractViewMock<ProductScope>(app, scope) {

    companion object {
        fun cast(view: CubeView?): ProductViewMock {
            Assertions.assertNotNull(view, "Expecting ProductViewMock but this view was null")
            Assertions.assertInstanceOf(ProductViewMock::class.java, view,
                "Expecting ProductViewMock but it was ${view!!::class.simpleName}")
            return view as ProductViewMock
        }
    }


    fun printProduct() {
        println("PRODUCT #${scope.product?.id}")
        println("Name: ${scope.product?.name}")
        println("Price: ${scope.product?.price}")
        println("Description: ${scope.product?.description}")
        println("Image: ${scope.product?.image}")
        println()
    }
}
