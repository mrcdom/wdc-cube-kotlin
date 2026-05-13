package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeViewState
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelViewState
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelViewState
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import org.junit.jupiter.api.Assertions
import java.util.Date

class RestrictedViewMock(
    app: ShoppingApplicationMock,
    presenter: HomePresenter,
) : AbstractViewMock<HomePresenter>(app, presenter) {

    companion object {
        fun cast(view: CubeView?): RestrictedViewMock {
            Assertions.assertNotNull(view, "Expecting RestrictedViewMock but this view was null")
            Assertions.assertInstanceOf(RestrictedViewMock::class.java, view,
                "Expecting RestrictedViewMock but it was ${view!!::class.simpleName}")
            return view as RestrictedViewMock
        }
    }

    var state: HomeViewState = presenter.state

    fun getProductsPanelState(): ProductsPanelViewState {
        return ProductsPanelViewMock.cast(state.productsPanelView).state
    }

    fun getPurchasesPanelState(): PurchasesPanelViewState {
        return PurchasesPanelViewMock.cast(state.purchasesPanelView).state
    }

    fun render() {
        println("Seja bem vindo, ${state.nickName}!")
        println()
        println("Carrinho[${state.cartItemCount}]")
        println()
        printCompras()
        printProdutos()
        println("---------------------------------------------------------")
    }

    fun printCompras() {
        val purchasesState = getPurchasesPanelState()
        for (compra: PurchaseInfo in purchasesState.purchases) {
            println("COMPRA #${compra.id}")
            println("Data da compra: ${Date(compra.date)}")
            println("Itens adquiridos: ${compra.items}")
            println("Valor total: R$ ${compra.total}")
            println()
        }
    }

    fun printProdutos() {
        val productsState = getProductsPanelState()
        for (produto: ProductInfo in productsState.products ?: emptyList()) {
            print("PRODUTO #${produto.id}")
            print("{nome: ")
            print(produto.name)
            print(", valor: ")
            print(produto.price)
            print(", imagem: ")
            print(produto.image)
            println("}")
        }
    }
}
