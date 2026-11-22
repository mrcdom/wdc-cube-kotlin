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
        println("Welcome, ${state.nickName}!")
        println()
        println("Cart[${state.cartItemCount}]")
        println()
        printPurchases()
        printProducts()
        println("---------------------------------------------------------")
    }

    fun printPurchases() {
        val purchasesState = getPurchasesPanelState()
        for (purchase: PurchaseInfo in purchasesState.purchases) {
            println("PURCHASE #${purchase.id}")
            println("Purchase date: ${Date(purchase.date)}")
            println("Items purchased: ${purchase.items}")
            println("Total: \$${purchase.total}")
            println()
        }
    }

    fun printProducts() {
        val productsState = getProductsPanelState()
        for (product: ProductInfo in productsState.products ?: emptyList()) {
            print("PRODUCT #${product.id}")
            print("{name: ")
            print(product.name)
            print(", price: ")
            print(product.price)
            print(", image: ")
            print(product.image)
            println("}")
        }
    }
}
