package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.RootKeys

/** The keys of the restricted branch (home and the places inside it). See [RootKeys]. */
open class HomeKeys protected constructor(app: RootPresenter, intent: CubeIntent?, place: CubeTreePlace) :
    RootKeys(app, intent, place) {

    constructor(app: RootPresenter, intent: CubeIntent? = null) : this(app, intent, PLACE)

    companion object {
        const val PREFIX = "hm"

        val PLACE: CubeTreePlace = RootKeys.PLACE.child("home", ::HomePresenter)
    }

    private object P {
        /** The page of the purchases panel (0 is the first). */
        const val PURCHASES_PAGE = "$PREFIX-page"
    }

    /** The page of the purchases panel: 0 or more; the first page (the default) leaves the address. */
    var purchasesPage: Int
        get() = intent.getParameterAsInteger(P.PURCHASES_PAGE)?.takeIf { it > 0 } ?: 0
        set(value) = intent.setParameter(P.PURCHASES_PAGE, value.takeIf { it > 0 })
}
