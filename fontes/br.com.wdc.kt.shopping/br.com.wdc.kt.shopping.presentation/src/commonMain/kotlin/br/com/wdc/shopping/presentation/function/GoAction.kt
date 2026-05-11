package br.com.wdc.shopping.presentation.function

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.ShoppingApplication

fun interface GoAction {
    fun apply(app: ShoppingApplication, intent: CubeIntent): Boolean
}
