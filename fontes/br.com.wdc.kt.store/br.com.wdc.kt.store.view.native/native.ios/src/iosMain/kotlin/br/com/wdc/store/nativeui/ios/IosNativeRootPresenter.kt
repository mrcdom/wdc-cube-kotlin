package br.com.wdc.store.nativeui.ios

import br.com.wdc.framework.commons.storage.IosPersistentSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.store.presentation.presenter.RootPresenter

/**
 * iOS-specific RootPresenter implementation.
 */
internal class IosNativeRootPresenter : RootPresenter() {

    override fun createSessionStorage(): SessionStorage = IosPersistentSessionStorage()
}
