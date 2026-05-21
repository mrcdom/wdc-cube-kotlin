package br.com.wdc.shopping.nativeui.ios

import br.com.wdc.framework.commons.storage.IosPersistentSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.shopping.presentation.ShoppingApplication

/**
 * iOS-specific ShoppingApplication implementation.
 */
internal class IosNativeShoppingApplication : ShoppingApplication() {

    override fun createSessionStorage(): SessionStorage = IosPersistentSessionStorage()
}
