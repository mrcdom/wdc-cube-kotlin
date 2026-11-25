package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.presentation.presenter.RootScope
import br.com.wdc.store.nativeui.web.bridge.RenderSlot
import br.com.wdc.store.nativeui.web.bridge.ScopeProps
import br.com.wdc.store.nativeui.web.bridge.useScope
import mui.material.Alert
import mui.material.Box
import mui.material.Button
import mui.material.Snackbar
import mui.system.sx
import react.FC
import react.ReactNode
import web.cssom.*

val RootView = FC<ScopeProps<RootScope>> { props ->
    val scope = useScope(props.scope)

    Box {
        sx {
            position = Position.relative
            minHeight = 100.vh
            display = Display.flex
            flexDirection = FlexDirection.column
        }

        RenderSlot { this.scope = scope.content }

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
