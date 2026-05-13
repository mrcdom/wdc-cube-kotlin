package br.com.wdc.shopping.presentation.presenter

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePlace
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.function.GoAction
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter

object Routes {

    // :: Navigation

    enum class Place(
        override val placeName: String,
        goAction: GoAction,
        private val factory: (ShoppingApplication) -> CubePresenter,
    ) : CubePlace {
        ROOT("public", GoAction { app, intent -> root(app, intent) }, ::RootPresenter),
        LOGIN("public/login", GoAction { app, intent -> login(app, intent) }, ::LoginPresenter),
        HOME("home", GoAction { app, intent -> home(app, intent) }, ::HomePresenter),
        CART("cart", GoAction { app, intent -> cart(app, intent) }, ::CartPresenter),
        PRODUCT("product", GoAction { app, intent -> product(app, intent) }, ::ProductPresenter),
        RECEIPT("receipt", GoAction { app, intent -> receipt(app, intent) }, ::ReceiptPresenter);

        override val id: Int get() = ordinal

        init {
            ShoppingApplication.Internals.registerPlace(placeName, goAction)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> CubePresenter =
            factory as (A) -> CubePresenter
    }

    // :: Root

    fun root(app: ShoppingApplication): Boolean = root(app, app.newIntent())

    fun root(app: ShoppingApplication, intent: CubeIntent): Boolean {
        return if (app.subject == null) login(app, intent) else home(app, intent)
    }

    // :: Login

    fun login(app: ShoppingApplication): Boolean = login(app, app.newIntent())

    fun login(app: ShoppingApplication, intent: CubeIntent): Boolean {
        return app.navigate<ShoppingApplication>()
            .step(Place.ROOT)
            .step(Place.LOGIN)
            .execute(intent)
    }

    // :: Home

    fun home(app: ShoppingApplication): Boolean = home(app, app.newIntent())

    fun home(app: ShoppingApplication, intent: CubeIntent): Boolean {
        return app.navigate<ShoppingApplication>()
            .step(Place.ROOT)
            .step(Place.HOME)
            .execute(intent)
    }

    // :: Cart

    fun cart(app: ShoppingApplication): Boolean = cart(app, app.newIntent())

    fun cart(app: ShoppingApplication, intent: CubeIntent): Boolean {
        return app.navigate<ShoppingApplication>()
            .step(Place.ROOT)
            .step(Place.HOME)
            .step(Place.CART)
            .execute(intent)
    }

    // :: Product

    fun product(app: ShoppingApplication, intent: CubeIntent): Boolean {
        return app.navigate<ShoppingApplication>()
            .step(Place.ROOT)
            .step(Place.HOME)
            .step(Place.PRODUCT)
            .execute(intent)
    }

    // :: Receipt

    fun receipt(app: ShoppingApplication, intent: CubeIntent): Boolean {
        return app.navigate<ShoppingApplication>()
            .step(Place.ROOT)
            .step(Place.HOME)
            .step(Place.RECEIPT)
            .execute(intent)
    }
}
