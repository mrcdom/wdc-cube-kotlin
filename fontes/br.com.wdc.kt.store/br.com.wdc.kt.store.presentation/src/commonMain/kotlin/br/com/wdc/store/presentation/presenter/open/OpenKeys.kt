package br.com.wdc.store.presentation.presenter.open

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.presentation.presenter.RootPresenter

/** The keys of the open branch (the places with no user). A guard place: alone, it goes on to the login. See [RootKeys]. */
open class OpenKeys protected constructor(app: RootPresenter, intent: CubeIntent?, place: CubeTreePlace) :
    RootKeys(app, intent, place) {

    constructor(app: RootPresenter, intent: CubeIntent? = null) : this(app, intent, PLACE)

    companion object {
        const val PREFIX = "op"

        val PLACE: CubeTreePlace = RootKeys.PLACE.child("open", ::OpenPresenter)
    }
}
