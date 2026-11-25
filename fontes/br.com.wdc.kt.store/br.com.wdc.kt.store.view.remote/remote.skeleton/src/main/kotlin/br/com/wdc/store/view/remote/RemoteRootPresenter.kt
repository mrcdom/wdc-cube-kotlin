package br.com.wdc.store.view.remote

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.storage.JvmSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.remote.RemoteApplication
import br.com.wdc.framework.cube.remote.RemoteApplicationRegistry
import br.com.wdc.framework.cube.remote.RemoteApplicationSupport
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootScope
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import kotlin.reflect.KClass

/**
 * The WDC Store as a remote application (`framework-cube-remote-host`): its root presenter, with a remote session around
 * it. The session's work — views, protocol, history, life — is the framework's ([RemoteApplicationSupport]); what is
 * the Store's is here: which client component paints each of its scopes ([VIDS]), that a signed-in user's session
 * outlives its connection, and the text of an unexpected error.
 */
class RemoteRootPresenter(id: String) : RootPresenter(), RemoteApplication {

    companion object {
        /** The vid of each scope the Store shows: each scope declares the one of the client component that paints it. */
        val VIDS: Map<KClass<out Scope>, String> = mapOf(
            RootScope::class to RootScope.VID,
            LoginScope::class to LoginScope.VID,
            HomeScope::class to HomeScope.VID,
            ProductScope::class to ProductScope.VID,
            CartScope::class to CartScope.VID,
            CartItemScope::class to CartItemScope.VID,
            ReceiptScope::class to ReceiptScope.VID,
            ProductsPanelScope::class to ProductsPanelScope.VID,
            PurchasesPanelScope::class to PurchasesPanelScope.VID,
        )

        /** The Store's remote sessions. */
        val REGISTRY = RemoteApplicationRegistry(::RemoteRootPresenter)
    }

    override val remote = RemoteApplicationSupport(id, this, VIDS, content = scope)

    override fun createSessionStorage(): SessionStorage = JvmSessionStorage()

    /** A signed-in user's session waits for the client to reconnect; an anonymous one ends with its connection. */
    override fun keepsSessionWithoutConnection(): Boolean = subject != null

    /** What the client ciphered with the session's key (the login's password). */
    override fun b64Decipher(b64Text: String): String = remote.dataSecurity.b64Decipher(b64Text)

    override fun updateHistory() = remote.updateHistory()

    /** The browser's alert shows the error; the stack trace goes to the server log, never to the client. */
    override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        val text = errorText(message, e)
        logger.error(text, e)
        remote.browser.alertUnexpectedError(text)
    }

    override fun release() {
        remote.release()
        super.release()
    }
}
