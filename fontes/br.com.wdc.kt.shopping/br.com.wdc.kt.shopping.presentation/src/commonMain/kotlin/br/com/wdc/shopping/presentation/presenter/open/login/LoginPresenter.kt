package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.domain.exception.OfflineException
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys

class LoginPresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("LoginPresenter")

        var createView: ((LoginPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val scope = bind(LoginScope())

    // :: Internal Instance Fields

    private val loginService = LoginService()
    private var ownerSlot: CubeViewSlot? = null

    init {
        scope.onEnter = actionOf2 { userName: String?, password: String? -> enter(userName, password) }
        // a remote view sends what the user typed by sync (the password ciphered), then the event
        onEvent(1) { scope.onEnter(null, null) }
    }

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = LoginKeys(app, intent).parentSlot
        if (initialization) {
            view = createView?.invoke(this)
        }

        ownerSlot?.setView(view!!)

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

    private fun alertConnectionError(caught: Throwable) {
        scope.errorCode = 5
        scope.errorMessage = "Failed to communicate with the server. Check your connection."
    }

    // :: User Actions

    private suspend fun enter(userName: String?, password: String?) {
        if (userName != null) scope.userName = userName
        if (password != null) scope.password = password

        scope.loading = true
        scope.errorCode = 0
        scope.errorMessage = null

        try {
            val subject = loginService.fetchSubject(scope.userName ?: "", scope.password ?: "")

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

            LOG.error("onEnter", caught)
            alertConnectionError(caught)
        }
    }
}
