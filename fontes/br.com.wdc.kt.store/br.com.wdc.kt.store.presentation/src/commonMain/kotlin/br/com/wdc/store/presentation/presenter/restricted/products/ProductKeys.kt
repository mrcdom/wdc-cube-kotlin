package br.com.wdc.store.presentation.presenter.restricted.products

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.presentation.presenter.restricted.home.HomeKeys

/** The keys of the product place. See [RootKeys]. */
class ProductKeys(app: RootPresenter, intent: CubeIntent? = null) : HomeKeys(app, intent, PLACE) {

    companion object {
        const val PREFIX = "pr"

        val PLACE: CubeTreePlace = HomeKeys.PLACE.child("product", ::ProductPresenter)
    }

    private object P {
        /** The product shown. */
        const val PRODUCT_ID = "$PREFIX-id"
    }

    /** A positive id, or `null` when the address carries none it can use. */
    var productId: Long?
        get() = intent.getParameterAsLong(P.PRODUCT_ID)?.takeIf { it > 0 }
        set(value) = intent.setParameter(P.PRODUCT_ID, value?.takeIf { it > 0 })
}
