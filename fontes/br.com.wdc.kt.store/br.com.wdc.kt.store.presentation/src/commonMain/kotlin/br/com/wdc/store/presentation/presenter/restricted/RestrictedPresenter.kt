package br.com.wdc.store.presentation.presenter.restricted

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.open.OpenKeys
import br.com.wdc.store.presentation.presenter.restricted.home.HomeKeys

/**
 * The guard of the restricted area (the places that need a signed-in user): no scope, no view — it only decides, on
 * every navigation that goes through it, whether the composition goes on. Without a user it cannot: the navigation
 * goes to the open area, with the intent in transit; a navigation that ends here, on a guard with no screen of its
 * own, goes on to the home. The slot of the level above passes through it untouched.
 */
class RestrictedPresenter(override val app: RootPresenter) : CubePresenter {

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (app.subject == null) {
            OpenKeys(app, intent).flip()
            return false
        }
        if (deepest) {
            HomeKeys(app, intent).flip()
            return false
        }
        return true
    }

    override fun release() {
        // NOOP: nothing of its own
    }
}
