package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootScope
import br.com.wdc.store.nativeui.web.bridge.RenderSlot
import br.com.wdc.store.nativeui.web.bridge.ReactCubeView
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

class RootView(
    app: RootPresenter,
    scope: RootScope,
) : ReactCubeView<RootScope>("root-view", app, scope) {

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@RootView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev


        Box {
            sx {
                position = Position.relative
                minHeight = 100.vh
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            val contentView = scope.content?.let(app::viewOf)
            if (contentView != null) {
                RenderSlot {
                    view = contentView
                }
            }

            val errorMessage = scope.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                Snackbar {
                    open = true
                    message = ReactNode(errorMessage)
                    autoHideDuration = 6000
                    onClose = { _, _ ->
                        scope.errorMessage = null
                    }
                }
            }
        }
    }
}
