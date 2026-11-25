package br.com.wdc.store.presentation.presenter.open.login

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.open.OpenKeys

/** The keys of the login place. See [RootKeys]. */
class LoginKeys(app: RootPresenter, intent: CubeIntent? = null) : OpenKeys(app, intent, PLACE) {

    companion object {
        const val PREFIX = "lg"

        val PLACE: CubeTreePlace = OpenKeys.PLACE.child("public/login", ::LoginPresenter)
    }

    private object A {
        /** Set when the server would not renew the session: the login tells the user why they are back. */
        const val SESSION_EXPIRED = "$PREFIX-session-expired"
    }

    /** Whether the user comes back to the login because the session expired (not from the address). */
    var sessionExpired: Boolean
        get() = intent.getAttribute(A.SESSION_EXPIRED) == true
        set(value) = intent.setAttribute(A.SESSION_EXPIRED, value.takeIf { it })
}
