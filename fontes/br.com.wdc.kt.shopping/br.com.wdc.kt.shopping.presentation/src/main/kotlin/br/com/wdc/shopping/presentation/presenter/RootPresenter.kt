package br.com.wdc.shopping.presentation.presenter

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.presentation.PlaceAttributes
import br.com.wdc.shopping.presentation.PlaceParameters
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.WrongPlace

class RootPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    // :: Public Class Fields

    companion object {
        private val LOG = Log.getLogger(RootPresenter::class.java)

        @JvmField
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

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (initialization) {
            view = createView?.invoke(this)
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

    // :: Slots

    private fun setContentView(view: CubeView) {
        if (state.contentView !== view) {
            state.contentView = view
            update()
        }
    }
}
