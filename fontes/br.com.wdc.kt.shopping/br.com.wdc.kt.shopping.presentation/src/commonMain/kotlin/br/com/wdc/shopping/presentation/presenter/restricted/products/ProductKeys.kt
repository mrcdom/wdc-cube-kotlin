package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys

/** The keys of the product place. See [RootKeys]. */
class ProductKeys(app: ShoppingApplication, intent: CubeIntent? = null) : HomeKeys(app, intent) {

    companion object {
        const val PREFIX = "pr"
    }

    private object P {
        /** The product shown. */
        const val PRODUCT_ID = "$PREFIX-id"
    }

    override suspend fun go(): Boolean = Routes.product(app, intent)

    /** A positive id, or `null` when the address carries none it can use. */
    var productId: Long?
        get() = intent.getParameterAsLong(P.PRODUCT_ID)?.takeIf { it > 0 }
        set(value) = intent.setParameter(P.PRODUCT_ID, value?.takeIf { it > 0 })
}
