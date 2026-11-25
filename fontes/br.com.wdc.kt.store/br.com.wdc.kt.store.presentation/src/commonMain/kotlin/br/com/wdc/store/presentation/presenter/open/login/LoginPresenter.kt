package br.com.wdc.store.presentation.presenter.open.login

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.framework.domain.exception.TooManyRequestsException
import br.com.wdc.store.domain.exception.OfflineException
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.store.presentation.presenter.open.login.calls.CredentialsCheck

class LoginPresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("LoginPresenter")
    }

    // :: Public Instance Fields

    val scope = bind(LoginScope())

    // :: Internal Instance Fields

    private var ownerSlot: ScopeSlot? = null

    init {
        scope.onEnter = action(1) { onEnter() }
        scope.onSync(::sync)
    }

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = LoginKeys(app, intent).parentSlot

        ownerSlot?.setScope(scope)

        if (LoginKeys(app, intent).sessionExpired) {
            alertSessionExpired()
        }

        return false
    }

    // :: Messages

    private fun alertUserOrPasswordNotRecognize() {
        scope.errorCode = 1
        scope.errorMessage = "Username or password not recognized!"
    }

    private fun alertDatabaseIsOffline() {
        scope.errorCode = 4
        scope.errorMessage = "The database is offline!"
    }

    private fun alertTooManyAttempts(retryAfterSeconds: Long?) {
        scope.errorCode = 6
        scope.errorMessage = if (retryAfterSeconds != null) {
            "Too many failed sign-ins. Try again in $retryAfterSeconds seconds."
        } else {
            "Too many failed sign-ins. Try again in a few minutes."
        }
    }

    private fun alertSessionExpired() {
        scope.errorCode = 7
        scope.errorMessage = "Your session has expired. Please sign in again."
    }

    private fun alertConnectionError(caught: Throwable) {
        scope.errorCode = 5
        scope.errorMessage = "Failed to communicate with the server. Check your connection."
    }

    // :: User Actions

    /**
     * What a remote view sends before firing enter (1): what the user typed, with the password ciphered with the
     * session's key.
     */
    private fun sync(formData: Map<String, Any?>) {
        if (formData.containsKey("userName")) {
            scope.userName = CoerceUtils.asString(formData["userName"])
        }
        if (formData.containsKey("password")) {
            scope.password = app.b64Decipher(CoerceUtils.asString(formData["password"]) ?: "")
        }
    }

    private suspend fun onEnter() {
        scope.loading = true
        scope.errorCode = 0
        scope.errorMessage = null

        try {
            val subject = CredentialsCheck(
                userName = scope.userName ?: "",
                password = scope.password ?: "",
            ).run()

            scope.loading = false

            if (subject == null || subject.id == null) {
                app.subject = null
                alertUserOrPasswordNotRecognize()
            } else {
                app.startSession(subject)
                HomeKeys(app).flip()
            }
        } catch (caught: Exception) {
            scope.loading = false

            if (caught is OfflineException) {
                alertDatabaseIsOffline()
                return
            }

            if (caught is TooManyRequestsException) {
                alertTooManyAttempts(caught.retryAfterSeconds)
                return
            }

            LOG.error("onEnter", caught)
            alertConnectionError(caught)
        }
    }
}
