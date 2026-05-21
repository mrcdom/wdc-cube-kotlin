package br.com.wdc.shopping.presentation.presenter

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.presentation.PlaceAttributes
import br.com.wdc.shopping.presentation.PlaceParameters
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.WrongPlace
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.SimpleSecurityContext
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject

class RootPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    // :: Public Class Fields

    companion object {
        private val LOG = Log.getLogger("RootPresenter")

        var createView: ((RootPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = RootViewState()

    // :: Internal Instance Fields

    private val contentSlot = CubeViewSlot { v -> setContentView(v) }

    // :: Cube API

    override fun release() {
        state.contentView = null
        super.release()
    }

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (initialization) {
            view = createView?.invoke(this)
            restoreSubjectFromSession()
        }

        if (deepest) {
            throw WrongPlace()
        } else {
            // Does not accept changing user id at URL
            if (app.subject != null) {
                intent.setParameter(PlaceParameters.USER_ID, app.subject!!.id)
            }
            intent.setViewSlot(PlaceAttributes.SLOT_OWNER, contentSlot)
        }

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        if (app.subject != null) {
            intent.setParameter(PlaceParameters.USER_ID, app.subject!!.id)
        }
    }

    // :: Messages

    fun alertUnexpectedError(message: String, caught: Exception) {
        alertUnexpectedError(LOG, message, caught)
    }

    fun alertUnexpectedError(logger: Log, message: String, caught: Throwable) {
        state.errorMessage = if (!caught.message.isNullOrBlank()) {
            message
        } else {
            "$message: ${caught.message}"
        }
        update()
        logger.error(state.errorMessage!!, caught)
    }

    // :: Internal

    private fun restoreSubjectFromSession() {
        if (app.subject != null) return

        val json = app.sessionStorage.getString("subject") ?: return
        try {
            val inp = JsonInputFactory.createStringInput(json).input
            val subject = Subject()
            subject.readExternal(inp)
            if (subject.id != null) {
                app.subject = subject
            }
        } catch (e: Exception) {
            LOG.error("restoreSubjectFromSession", e)
            app.sessionStorage.remove("subject")
        }

        restoreSecurityContextFromSession()
        restoreAuthStateFromSession()
    }

    private fun restoreSecurityContextFromSession() {
        if (app.getSecurityContext() != null) return

        val json = app.sessionStorage.getString("securityContext") ?: return
        try {
            val inp = JsonInputFactory.createStringInput(json).input
            val ctx = SimpleSecurityContext()
            ctx.readExternal(inp)
            if (ctx.userId != null) {
                app.setSecurityContext(ctx)
            }
        } catch (e: Exception) {
            LOG.error("restoreSecurityContextFromSession", e)
            app.sessionStorage.remove("securityContext")
        }
    }

    private fun restoreAuthStateFromSession() {
        val json = app.sessionStorage.getString("authState") ?: return
        try {
            val authService = AuthenticationService.BEAN.getOrNull() ?: return
            val inp = JsonInputFactory.createStringInput(json).input
            authService.readAuthState(inp)
        } catch (e: Exception) {
            LOG.error("restoreAuthStateFromSession", e)
            app.sessionStorage.remove("authState")
        }
    }

    // :: Slots

    private fun setContentView(view: CubeView) {
        if (state.contentView !== view) {
            state.contentView = view
            update()
        }
    }
}
