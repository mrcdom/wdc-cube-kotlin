package br.com.wdc.store.presentation.presenter.open.login

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootKeys

/**
 * The keys of the login place. It has no names of its own yet, but every place has its keys: it is where they will
 * go, and where navigating to the place starts. See [RootKeys].
 */
class LoginKeys(app: RootPresenter, intent: CubeIntent? = null) : RootKeys(app, intent, PLACE) {

    companion object {
        const val PREFIX = "lg"

        val PLACE: CubeTreePlace = RootKeys.PLACE.child("public/login", ::LoginPresenter)
    }
}
