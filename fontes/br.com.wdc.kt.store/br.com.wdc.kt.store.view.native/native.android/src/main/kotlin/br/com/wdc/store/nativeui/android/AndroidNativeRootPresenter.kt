package br.com.wdc.store.nativeui.android

import br.com.wdc.framework.commons.storage.AndroidPersistentSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.store.presentation.presenter.RootPresenter

internal class AndroidNativeRootPresenter(
    private val persistentStorage: AndroidPersistentSessionStorage
) : RootPresenter() {

    override fun updateHistory() { /* No browser history on Android */ }

    override fun createSessionStorage(): SessionStorage = persistentStorage
}
