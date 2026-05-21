package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.shopping.nativeui.web.bridge.RenderSlot
import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import mui.material.Alert
import mui.material.Box
import mui.material.Button
import mui.material.Snackbar
import mui.system.sx
import react.FC
import react.Props
import react.ReactNode
import react.useEffect
import react.useState
import web.cssom.*

class RootView(private val presenter: RootPresenter) : ReactCubeView("root-view", presenter) {

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@RootView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val state = presenter.state

        Box {
            sx {
                position = Position.relative
                minHeight = 100.vh
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            val contentView = state.contentView
            if (contentView != null) {
                RenderSlot {
                    view = contentView
                }
            }

            val errorMessage = state.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                Snackbar {
                    open = true
                    message = ReactNode(errorMessage)
                    autoHideDuration = 6000
                    onClose = { _, _ ->
                        state.errorMessage = null
                        presenter.update()
                    }
                }
            }
        }
    }
}
