package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractPresenter
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.FormParam
import br.com.wdc.framework.cube.Scope

/**
 * Binds the browser tab's actions ([BrowserScope]) with their event codes, as a presenter binds its scope's: the
 * remote view of the tab is a plain [RemoteViewImpl]. It is not a place: the session ([RemoteApplicationSupport])
 * creates it with itself, around the application's root scope ([content]).
 */
class RemoteBrowserPresenter(
    app: CubeApplication,
    private val session: RemoteApplicationSupport,
    content: Scope,
) : AbstractPresenter<CubeApplication>(app) {

    companion object {
        private val LOG = Log.getLogger("RemoteBrowserPresenter")

        /** The id of the browser's view: always instance 0. */
        const val VSID = "${BrowserScope.VID}:0"
    }

    val scope = bind(BrowserScope())

    /** What runs when the user dismisses the alert on screen. */
    private var alertAction: () -> Unit = {}

    init {
        scope.content = content
        scope.onAlertOk = action(1) { dismissAlert() }
        scope.onKeepAlive = action(2) { session.extendLife() }
        scope.onStart = actionOf(-1, FormParam.string("p.path")) { start(it) }
        scope.onHistoryChanged = actionOf(-2, FormParam.string("p.path")) { goTo(it) }
    }

    /** Shows [message] in the alert; only the text goes to the client, never a stack trace. */
    fun alertUnexpectedError(message: String) {
        alert({}, -1, message.ifBlank { "An unexpected error occurred" })
    }

    /** Shows the alert of [code] (the client turns it and [args] into its text); [action] runs when it is dismissed. */
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
            session.safeGo(path)
            session.updateAllViews()
        } catch (e: Exception) {
            LOG.warn("onStart", e)
        }
    }

    private suspend fun goTo(path: String?) {
        try {
            session.safeGo(path)
        } catch (e: Exception) {
            LOG.warn("onHistoryChanged", e)
        }
    }
}
