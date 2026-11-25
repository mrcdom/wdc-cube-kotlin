package br.com.wdc.store.view.remote.react.kotlin.views

import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.store.view.remote.react.kotlin.theme.StoreColors
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mui.icons.material.LocalMall
import mui.material.Alert
import mui.material.Avatar
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Card
import mui.material.CardContent
import mui.material.CircularProgress
import mui.material.Stack
import mui.material.StackDirection
import mui.material.TextField
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.ReactNode
import react.dom.events.KeyboardEventHandler
import react.dom.html.ReactHTML.form
import react.useRef
import web.cssom.*
import web.html.HTMLDivElement
import web.html.HTMLInputElement

// :: Events of the login view
private const val ON_ENTER = 1

private val loginScope = MainScope()

/**
 * The sign-in: the user name goes as typed and the password ciphered with the session's key, both as form fields
 * (`userName`, `password`) that the server syncs into the login scope before it runs the event.
 */
val LoginView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)

    // Uncontrolled fields: typing repaints nothing; the values are read on submit
    val userNameRef = useRef<HTMLInputElement>(null)
    val passwordRef = useRef<HTMLInputElement>(null)

    val emitOnEnter: () -> Unit = {
        val userName = userNameRef.current?.value ?: ""
        val password = passwordRef.current?.value ?: ""
        loginScope.launch {
            try {
                bridge.setFormField(props.vsid, "userName", userName)
                bridge.setFormField(props.vsid, "password", bridge.cipher(password))
                bridge.submit(props.vsid, ON_ENTER)
            } catch (e: Throwable) {
                console.error("Sign-in: the password could not be ciphered", e)
            }
        }
    }

    val onPasswordKeyDown: KeyboardEventHandler<HTMLDivElement> = { e ->
        if (e.key == "Enter") {
            e.preventDefault()
            emitOnEnter()
        }
    }

    val loading = scope.boolean("loading")

    Box {
        sx {
            minHeight = 100.vh
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            background =
                "linear-gradient(180deg, ${StoreColors.Primary} 0%, ${StoreColors.PrimaryContainer} 100%)".unsafeCast<Background>()
        }

        Card {
            sx {
                width = 400.px
                borderRadius = 12.px
                boxShadow = "0px 2px 8px rgba(0,0,0,0.12)".unsafeCast<BoxShadow>()
            }

            CardContent {
                sx { padding = 24.px }

                form {
                    asDynamic().autoComplete = "off"
                    asDynamic().onSubmit = { e: dynamic -> e.preventDefault() }

                    Stack {
                        direction = responsive(StackDirection.column)
                        spacing = responsive(2)
                        sx { alignItems = AlignItems.center }

                        // Logo
                        Avatar {
                            sx {
                                width = 72.px
                                height = 72.px
                                backgroundColor = StoreColors.Primary.unsafeCast<BackgroundColor>()
                                borderRadius = 18.px
                            }
                            LocalMall { sx { fontSize = 40.px } }
                        }

                        Box {
                            sx { position = Position.relative }
                            Typography {
                                variant = TypographyVariant.h5
                                sx { fontWeight = FontWeight.bold }
                                +"WDC Store"
                            }
                            Typography {
                                variant = TypographyVariant.caption
                                sx {
                                    position = Position.absolute
                                    bottom = (-16).px
                                    right = 0.px
                                    color = StoreColors.OnSurfaceVariant.unsafeCast<Color>()
                                    opacity = number(0.5)
                                    fontSize = 10.px
                                }
                                +"kotlin"
                            }
                        }

                        Typography {
                            variant = TypographyVariant.body2
                            sx {
                                color = StoreColors.OnSurfaceVariant.unsafeCast<Color>()
                                textAlign = TextAlign.center
                            }
                            +"Sign in to continue"
                        }

                        // Username
                        TextField {
                            fullWidth = true
                            label = ReactNode("Username")
                            name = "login-usr"
                            inputRef = userNameRef
                            defaultValue = scope.string("userName") ?: ""
                            disabled = loading
                            asDynamic().type = "text"
                            asDynamic().autoComplete = "off"
                        }

                        // Password: a text field drawn as discs, so password managers leave it alone
                        TextField {
                            fullWidth = true
                            label = ReactNode("Password")
                            name = "login-pwd"
                            inputRef = passwordRef
                            defaultValue = ""
                            disabled = loading
                            asDynamic().type = "text"
                            asDynamic().autoComplete = "off"
                            asDynamic().inputProps = js("({style: {WebkitTextSecurity: 'disc', textSecurity: 'disc'}})")
                            onKeyDown = onPasswordKeyDown
                        }

                        // Error message
                        val errorMessage = scope.string("errorMessage")
                        if (!errorMessage.isNullOrBlank()) {
                            Alert {
                                severity = "error"
                                sx { width = 100.pct; borderRadius = 8.px }
                                +errorMessage
                            }
                        }

                        // Sign-in button
                        Button {
                            fullWidth = true
                            variant = ButtonVariant.contained
                            disabled = loading
                            asDynamic().type = "button"
                            onClick = { emitOnEnter() }
                            sx {
                                height = 48.px
                                borderRadius = 12.px
                                fontWeight = FontWeight.bold
                                fontSize = 16.px
                                textTransform = None.none
                            }

                            if (loading) {
                                CircularProgress {
                                    size = 24
                                    sx { color = NamedColor.white }
                                }
                            } else {
                                +"Sign in"
                            }
                        }
                    }
                }
            }
        }
    }
}
