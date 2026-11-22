package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.MainKeys
import br.com.wdc.shopping.presentation.presenter.Routes

/** The keys of the restricted branch (home and the places inside it). See [MainKeys]. */
open class HomeKeys(app: ShoppingApplication, intent: CubeIntent? = null) : MainKeys(app, intent) {

    companion object {
        const val PREFIX = "hm"
    }

    override suspend fun go(): Boolean = Routes.home(app, intent)
}
