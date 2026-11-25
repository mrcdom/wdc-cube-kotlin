package br.com.wdc.store.view.remote.react.kotlin.views

import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.framework.cube.remote.bridge.react.remoteView
import mui.material.Alert
import mui.material.AlertTitle
import mui.material.Box
import mui.material.Button
import mui.material.ButtonColor
import mui.material.CircularProgress
import mui.material.LinearProgress
import mui.material.Link
import mui.material.LinkUnderline
import mui.material.Size
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.FC
import react.Fragment
import react.Props
import react.create
import react.dom.html.ReactHTML.span
import web.cssom.*

// :: Events of the browser view
private const val ON_ALERT_OK = 1

/**
 * The browser tab: the progress bar while a request waits, the "not connected" bar while the bridge reconnects, the
 * host's alert, and the content (the root view).
 */
val BrowserView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)

    // Keyed children: a bar that comes and goes must not shift the content, which would remount the whole page
    Box {
        if (scope.boolean("submitting")) {
            LinearProgress {
                key = "submitting"
                sx {
                    position = Position.fixed
                    top = 0.px
                    left = 0.px
                    right = 0.px
                    zIndex = integer(9999)
                }
            }
        }

        scope.obj("error")?.let { error ->
            ConnectionAlert {
                key = "connection"
                delay = error.int("delay") ?: 0
                onReconnectNow = { bridge.connect() }
            }
        }

        scope.obj("alertMessage")?.let { alert ->
            AppAlert {
                key = "alert"
                code = alert.int("id") ?: 0
                args = alert.strings("args").toTypedArray()
                onDismiss = { bridge.submit(props.vsid, ON_ALERT_OK) }
            }
        }

        val contentViewId = scope.viewId("content")
        if (contentViewId != null) {
            Fragment {
                key = "content"
                remoteView(contentViewId)
            }
        } else {
            Box {
                key = "loading"
                sx {
                    display = Display.flex
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                    minHeight = 100.vh
                }
                CircularProgress()
            }
        }
    }
}

// :: The host's alert

private external interface AppAlertProps : Props {
    var code: Int
    var args: Array<String>
    var onDismiss: () -> Unit
}

private val AppAlert = FC<AppAlertProps> { props ->
    val args = props.args
    val message: String
    var detail: String? = null
    when (props.code) {
        -1 -> {
            message = args.getOrNull(0) ?: ""
            detail = args.getOrNull(1)
        }
        -2 -> {
            message = "The URI ${args.getOrNull(0)} is not reachable"
            detail = args.getOrNull(1)
        }
        else -> message = args.getOrNull(0) ?: "An unexpected error occurred"
    }

    Alert {
        severity = "warning"
        sx { margin = 16.px }
        action = Button.create {
            color = ButtonColor.inherit
            size = Size.small
            onClick = { props.onDismiss() }
            +"Ok"
        }
        AlertTitle { +"Warning!" }
        +message
        if (!detail.isNullOrEmpty()) {
            Typography {
                variant = TypographyVariant.body2
                sx { marginTop = 8.px }
                +detail
            }
        }
    }
}

// :: The "not connected" bar

private external interface ConnectionAlertProps : Props {
    var delay: Int
    var onReconnectNow: () -> Unit
}

private val ConnectionAlert = FC<ConnectionAlertProps> { props ->
    Box {
        sx { textAlign = TextAlign.center }
        Alert {
            severity = "warning"
            asDynamic().icon = false
            sx {
                display = Display.inlineFlex
                borderRadius = "0 0 4px 4px".unsafeCast<BorderRadius>()
                paddingTop = 0.px
                paddingBottom = 0.px
                paddingLeft = 12.px
                paddingRight = 12.px
            }

            Typography {
                component = span
                variant = TypographyVariant.body2
                sx { fontWeight = FontWeight.bold }
                +"Not connected."
            }
            +" "
            if (props.delay > 0) {
                var seconds = props.delay / 1000
                var minutes = 0
                if (seconds > 60) {
                    minutes = seconds / 60
                    seconds -= minutes * 60
                }
                Typography {
                    component = span
                    variant = TypographyVariant.body2
                    +(if (minutes > 0) "Connecting in ${minutes}m ${seconds}s..." else "Connecting in ${seconds}s...")
                }
                +" "
                Link {
                    asDynamic().component = "button"
                    variant = TypographyVariant.body2
                    underline = LinkUnderline.always
                    onClick = { props.onReconnectNow() }
                    sx { cursor = Cursor.pointer; marginLeft = 8.px }
                    +"Try now"
                }
            } else {
                Typography {
                    component = span
                    variant = TypographyVariant.body2
                    +"Connecting now..."
                }
            }
            +"."
        }
    }
}
