package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.structs.PurchaseInfo
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.store.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions
import java.util.Date

class RestrictedViewMock(
    app: RootPresenterMock,
    scope: HomeScope,
) : AbstractViewMock<HomeScope>(app, scope) {

    companion object {
        fun cast(view: CubeView?): RestrictedViewMock {
            Assertions.assertNotNull(view, "Expecting RestrictedViewMock but this view was null")
            Assertions.assertInstanceOf(RestrictedViewMock::class.java, view,
                "Expecting RestrictedViewMock but it was ${view!!::class.simpleName}")
            return view as RestrictedViewMock
        }
    }


    fun getProductsPanelScope(): ProductsPanelScope {
        return ProductsPanelViewMock.cast(scope.productsPanel?.let(app.views::viewOf)).scope
    }

    fun getPurchasesPanelScope(): PurchasesPanelScope {
        return PurchasesPanelViewMock.cast(scope.purchasesPanel?.let(app.views::viewOf)).scope
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
        val purchasesState = getPurchasesPanelScope()
        for (purchase: PurchaseInfo in purchasesState.purchases) {
            println("PURCHASE #${purchase.id}")
            println("Purchase date: ${Date(purchase.date)}")
            println("Items purchased: ${purchase.items}")
            println("Total: \$${purchase.total}")
            println()
        }
    }

    fun printProducts() {
        val productsState = getProductsPanelScope()
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

    /** The view of the scope below (asked to the application, as a real view does). */
    val contentView: CubeView? get() = scope.content?.let(app.views::viewOf)
}
