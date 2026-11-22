package br.com.wdc.shopping.presentation.presenter.restricted.cart

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.MainKeys
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys

/**
 * The keys of the cart place. It has no names of its own yet, but every place has its keys: it is where they will
 * go, and where navigating to the place starts. See [MainKeys].
 */
class CartKeys(app: ShoppingApplication, intent: CubeIntent? = null) : HomeKeys(app, intent) {

    companion object {
        const val PREFIX = "ct"
    }

    override suspend fun go(): Boolean = Routes.cart(app, intent)
}
