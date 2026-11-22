package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.Routes

/**
 * The keys of the login place. It has no names of its own yet, but every place has its keys: it is where they will
 * go, and where navigating to the place starts. See [RootKeys].
 */
class LoginKeys(app: ShoppingApplication, intent: CubeIntent? = null) : RootKeys(app, intent) {

    companion object {
        const val PREFIX = "lg"
    }

    override suspend fun go(): Boolean = Routes.login(app, intent)
}
