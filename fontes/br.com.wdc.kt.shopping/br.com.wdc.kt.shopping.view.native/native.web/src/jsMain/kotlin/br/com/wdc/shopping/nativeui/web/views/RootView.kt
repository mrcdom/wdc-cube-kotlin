package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.shopping.nativeui.web.bridge.RenderSlot
import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.bridge.WorkerProxy
import mui.material.Box
import mui.material.Snackbar
import mui.system.sx
import react.FC
import react.Props
import react.ReactNode
import react.useEffect
import react.useState
import web.cssom.*

class RootView(viewId: String, proxy: WorkerProxy) : ReactCubeView(viewId, proxy) {

    // Local state
    private var contentViewId: String? = null
    private var errorMessage: String? = null

    override fun readState(json: String) {
        // Reset nullable fields — absent from JSON means null
        contentViewId = null
        errorMessage = null

        val inp = JsonInputFactory.createStringInput(json).input
        inp.beginObject()
        while (inp.hasNext()) {
            when (inp.nextName()) {
                "id" -> inp.skipValue()
                "contentViewId" -> contentViewId = inp.nextString()
                "errorMessage" -> errorMessage = inp.nextString()
                else -> inp.skipValue()
            }
        }
        inp.endObject()
    }

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@RootView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val contentView = contentViewId?.let { proxy.getView(it) }

        Box {
            sx {
                position = Position.relative
                minHeight = 100.vh
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            if (contentView != null) {
                RenderSlot {
                    view = contentView
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Snackbar {
                    open = true
                    message = ReactNode(errorMessage!!)
                    autoHideDuration = 6000
                    onClose = { _, _ ->
                        errorMessage = null
                        update()
                    }
                }
            }
        }
    }
}
