package br.com.wdc.store.presentation.presenter.open

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.open.login.LoginKeys
import br.com.wdc.store.presentation.presenter.restricted.RestrictedKeys

/**
 * The guard of the open area (the places for someone not signed in): no scope, no view — it only decides, on every
 * navigation that goes through it, whether the composition goes on. A signed-in user has no reason to be here and
 * goes to the restricted area; a navigation that ends here goes on to the login. The slot of the level above passes
 * through it untouched.
 */
class OpenPresenter(override val app: RootPresenter) : CubePresenter {

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (app.subject != null) {
            RestrictedKeys(app, intent).flip()
            return false
        }
        if (deepest) {
            LoginKeys(app, intent).flip()
            return false
        }
        return true
    }

    override fun release() {
        // NOOP: nothing of its own
    }
}
