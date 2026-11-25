package br.com.wdc.store.presentation.presenter.open.login

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.framework.domain.exception.TooManyRequestsException
import br.com.wdc.store.domain.exception.OfflineException
import br.com.wdc.store.domain.security.AuthenticationService
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
        // a remote view syncs what the user typed (the password ciphered, deciphered by the remote layer), then fires 1
        scope.onEnter = action(1) { enter() }
    }

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = LoginKeys(app, intent).parentSlot

        ownerSlot?.setScope(scope)

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

    private fun alertConnectionError(caught: Throwable) {
        scope.errorCode = 5
        scope.errorMessage = "Failed to communicate with the server. Check your connection."
    }

    // :: User Actions

    private suspend fun enter() {
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
                app.subject = subject

                // Serialize subject to sessionStorage
                val out = JsonOutputFactory.createStringOutput()
                subject.writeExternal(out.output)
                app.sessionStorage.set("subject", out.resultString())

                // Serialize auth tokens to sessionStorage
                val authService = AuthenticationService.BEAN.getOrNull()
                if (authService != null) {
                    val authOut = JsonOutputFactory.createStringOutput()
                    authService.writeAuthState(authOut.output)
                    app.sessionStorage.set("authState", authOut.resultString())
                }

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
