package br.com.wdc.store.presentation.presenter.restricted

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.presentation.presenter.RootPresenter

/**
 * The keys of the restricted branch (the places that need a user). A guard place: alone, it goes on to the home. See
 * [RootKeys].
 */
open class RestrictedKeys protected constructor(app: RootPresenter, intent: CubeIntent?, place: CubeTreePlace) :
    RootKeys(app, intent, place) {

    constructor(app: RootPresenter, intent: CubeIntent? = null) : this(app, intent, PLACE)

    companion object {
        const val PREFIX = "rs"

        val PLACE: CubeTreePlace = RootKeys.PLACE.child("restricted", ::RestrictedPresenter)
    }
}
