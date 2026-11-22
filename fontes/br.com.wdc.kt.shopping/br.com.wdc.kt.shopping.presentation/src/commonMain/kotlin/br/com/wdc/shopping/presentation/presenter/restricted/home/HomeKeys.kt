package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.Routes

/** The keys of the restricted branch (home and the places inside it). See [RootKeys]. */
open class HomeKeys(app: ShoppingApplication, intent: CubeIntent? = null) : RootKeys(app, intent) {

    companion object {
        const val PREFIX = "hm"
    }

    private object P {
        /** The page of the purchases panel (0 is the first). */
        const val PURCHASES_PAGE = "$PREFIX-page"
    }

    override suspend fun go(): Boolean = Routes.home(app, intent)

    /** The page of the purchases panel: 0 or more; the first page (the default) leaves the address. */
    var purchasesPage: Int
        get() = intent.getParameterAsInteger(P.PURCHASES_PAGE)?.takeIf { it > 0 } ?: 0
        set(value) = intent.setParameter(P.PURCHASES_PAGE, value.takeIf { it > 0 })
}
