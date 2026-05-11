package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.getLogger
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.domain.exception.OfflineException
import br.com.wdc.shopping.presentation.PlaceAttributes
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.Routes

class LoginPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger(LoginPresenter::class.java)

        @JvmField
        var createView: ((LoginPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = LoginViewState()

    // :: Internal Instance Fields

    private val loginService = LoginService(app)
    private var ownerSlot: CubeViewSlot? = null

    // :: Cube API

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (initialization) {
            ownerSlot = intent.getViewSlot(PlaceAttributes.SLOT_OWNER)
            view = createView?.invoke(this)
        }

        ownerSlot?.setView(view!!)

        return false
    }

    // :: Messages

    private fun alertUserOrPasswordNotRecognize() {
        state.errorCode = 1
        state.errorMessage = "Usuário ou senha não reconhecido!"
        update()
    }

    private fun alertDatabaseIsOffline() {
        state.errorCode = 4
        state.errorMessage = "Banco de dados esta fora do ar!"
        update()
    }

    private fun alertConnectionError(caught: Throwable) {
        state.errorCode = 5
        state.errorMessage = "Falha de comunicação com o servidor. Verifique sua conexão."
        update()
    }

    // :: User Actions

    fun onEnter() {
        try {
            val subject = loginService.fetchSubject(state.userName ?: "", state.password ?: "")

            if (subject == null || subject.id == null) {
                app.subject = null
                alertUserOrPasswordNotRecognize()
            } else {
                app.subject = subject
                Routes.home(app)
            }
        } catch (caught: Exception) {
            if (caught is OfflineException) {
                alertDatabaseIsOffline()
                return
            }

            LOG.error("onEnter", caught)
            alertConnectionError(caught)
        }
    }
}
