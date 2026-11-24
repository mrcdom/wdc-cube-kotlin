package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.shopping.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions
import java.util.Date

class RestrictedViewMock(
    app: RootPresenterMock,
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

    var scope: HomeScope = presenter.scope

    fun getProductsPanelState(): ProductsPanelScope {
        return ProductsPanelViewMock.cast(scope.productsPanelView).scope
    }

    fun getPurchasesPanelState(): PurchasesPanelScope {
        return PurchasesPanelViewMock.cast(scope.purchasesPanelView).scope
    }

    fun render() {
        println("Welcome, ${scope.nickName}!")
        println()
        println("Cart[${scope.cartItemCount}]")
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
