package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductViewState
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import org.junit.jupiter.api.Assertions

class ProductViewMock(
    app: ShoppingApplicationMock,
    presenter: ProductPresenter,
) : AbstractViewMock<ProductPresenter>(app, presenter) {

    companion object {
        fun cast(view: CubeView?): ProductViewMock {
            Assertions.assertNotNull(view, "Expecting ProductViewMock but this view was null")
            Assertions.assertInstanceOf(ProductViewMock::class.java, view,
                "Expecting ProductViewMock but it was ${view!!::class.simpleName}")
            return view as ProductViewMock
        }
    }

    var state: ProductViewState = presenter.state

    fun printProduto() {
        println("PRODUTO #${state.product?.id}")
        println("Nome: ${state.product?.name}")
        println("Preço: ${state.product?.price}")
        println("Descrição: ${state.product?.description}")
        println("Imagem: ${state.product?.image}")
        println()
    }
}
