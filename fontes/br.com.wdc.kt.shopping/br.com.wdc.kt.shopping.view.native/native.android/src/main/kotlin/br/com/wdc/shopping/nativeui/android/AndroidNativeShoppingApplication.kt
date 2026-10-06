package br.com.wdc.shopping.nativeui.android

import br.com.wdc.framework.commons.storage.AndroidPersistentSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.shopping.presentation.ShoppingApplication

internal class AndroidNativeShoppingApplication(
    private val persistentStorage: AndroidPersistentSessionStorage
) : ShoppingApplication() {

    override fun updateHistory() { /* No browser history on Android */ }

    override fun createSessionStorage(): SessionStorage = persistentStorage
}
