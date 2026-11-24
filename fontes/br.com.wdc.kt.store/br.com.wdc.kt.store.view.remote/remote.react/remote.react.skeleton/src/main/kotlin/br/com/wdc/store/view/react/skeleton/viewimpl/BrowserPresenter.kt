package br.com.wdc.store.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractPresenter
import br.com.wdc.framework.cube.FormParam

/**
 * Binds the browser tab's actions ([BrowserScope]) with their event codes, as a presenter binds its scope's: the
 * remote view of the tab is a plain scope view. It is not a place: the application creates it with itself.
 */
class BrowserPresenter(app: ReactRootPresenter) : AbstractPresenter<ReactRootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("BrowserPresenter")

        const val VID = "7b32e816a191"
        const val VSID = "$VID:0"
    }

    val scope = bind(BrowserScope())

    /** What runs when the user dismisses the alert on screen. */
    private var alertAction: () -> Unit = {}

    init {
        scope.content = app.scope
        scope.onAlertOk = action(1) { dismissAlert() }
        scope.onKeepAlive = action(2) { app.extendLife() }
        scope.onStart = actionOf(-1, FormParam.string("p.path")) { start(it) }
        scope.onHistoryChanged = actionOf(-2, FormParam.string("p.path")) { goTo(it) }
    }

    /** Shows [message] in the alert; only the text goes to the client, never a stack trace. */
    fun alertUnexpectedError(message: String) {
        alert({}, -1, message.ifBlank { "An unexpected error occurred" })
    }

    fun alert(action: () -> Unit, code: Int, vararg args: Any?) {
        val previous = alertAction
        alertAction = {
            previous()
            action()
        }
        // a new instance every time: the same message again is still a new alert
        scope.alertMessage = BrowserAlert(code, args.map { it.toString() })
    }

    private fun dismissAlert() {
        try {
            scope.alertMessage = null
            alertAction()
        } finally {
            alertAction = {}
        }
    }

    private suspend fun start(path: String?) {
        try {
            app.safeGo(path)
            app.updateAllViews()
        } catch (e: Exception) {
            LOG.warn("onStart", e)
        }
    }

    private suspend fun goTo(path: String?) {
        try {
            app.safeGo(path)
        } catch (e: Exception) {
            LOG.warn("onHistoryChanged", e)
        }
    }
}
