package br.com.wdc.store.view.remote.react.kotlin.views

import br.com.wdc.store.i18n.*
import br.com.wdc.store.view.remote.react.kotlin.util.messages
import br.com.wdc.store.view.remote.react.kotlin.util.message
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.framework.cube.remote.bridge.react.remoteView
import mui.material.Alert
import mui.material.Box
import mui.system.sx
import react.FC
import web.cssom.px

/** The root place: its content (the login or the home), or the error that kept it from opening. */
val RootView = FC<RemoteViewProps> { props ->
    val scope = useBridge().useView(props.vsid)

    val errorMessage = scope.message("errorMessage")
    val contentViewId = scope.viewId("content")
    when {
        !errorMessage.isNullOrEmpty() -> Box {
            sx { margin = StoreTokens.Spacing.Lg.px }
            Alert {
                severity = "error"
                +errorMessage
            }
        }

        contentViewId != null -> remoteView(contentViewId)

        else -> Box {
            sx { margin = StoreTokens.Spacing.Lg.px }
            Alert {
                severity = "error"
                +messages.browserMissingHomeContent()
            }
        }
    }
}
