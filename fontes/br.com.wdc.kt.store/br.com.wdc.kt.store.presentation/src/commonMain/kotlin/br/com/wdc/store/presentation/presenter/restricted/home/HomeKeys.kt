package br.com.wdc.store.presentation.presenter.restricted.home

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.restricted.RestrictedKeys

/** The keys of the home and the places inside it. See [RootKeys]. */
open class HomeKeys protected constructor(app: RootPresenter, intent: CubeIntent?, place: CubeTreePlace) :
    RestrictedKeys(app, intent, place) {

    constructor(app: RootPresenter, intent: CubeIntent? = null) : this(app, intent, PLACE)

    companion object {
        const val PREFIX = "hm"

        val PLACE: CubeTreePlace = RestrictedKeys.PLACE.child("home", ::HomePresenter)
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
