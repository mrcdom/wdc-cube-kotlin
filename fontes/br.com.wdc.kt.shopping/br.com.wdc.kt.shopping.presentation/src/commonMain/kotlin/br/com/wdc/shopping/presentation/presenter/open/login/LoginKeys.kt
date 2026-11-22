package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.MainKeys
import br.com.wdc.shopping.presentation.presenter.Routes

/**
 * The keys of the login place. It has no names of its own yet, but every place has its keys: it is where they will
 * go, and where navigating to the place starts. See [MainKeys].
 */
class LoginKeys(app: ShoppingApplication, intent: CubeIntent? = null) : MainKeys(app, intent) {

    companion object {
        const val PREFIX = "lg"
    }

    override suspend fun go(): Boolean = Routes.login(app, intent)
}
