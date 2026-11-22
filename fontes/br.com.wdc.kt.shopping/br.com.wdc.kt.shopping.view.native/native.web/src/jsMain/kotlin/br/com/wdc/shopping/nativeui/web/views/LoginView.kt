package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.theme.ShoppingColors
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import mui.icons.material.LocalMall
import mui.material.Alert
import mui.material.Avatar
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Card
import mui.material.CardContent
import mui.material.CircularProgress
import mui.material.Size
import mui.material.Stack
import mui.material.StackDirection
import mui.material.TextField
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Props
import react.ReactNode
import react.RefCallback
import react.dom.events.KeyboardEventHandler
import react.dom.events.MouseEventHandler
import react.dom.html.ReactHTML.form
import react.useEffect
import react.useState
import web.cssom.*
import web.html.HTMLButtonElement
import web.html.HTMLDivElement
import web.html.HTMLInputElement

class LoginView(private val presenter: LoginPresenter) : ReactCubeView("login-view", presenter) {

    // :: Element refs — the fields are uncontrolled: typing repaints nothing, and the values are read on submit

    private var userNameInput: HTMLInputElement? = null
    private var passwordInput: HTMLInputElement? = null
    private val userNameRef = RefCallback<HTMLInputElement> { userNameInput = it }
    private val passwordRef = RefCallback<HTMLInputElement> { passwordInput = it }

    // :: Emitters — created once per view, so they are the same function on every render

    private val emitOnEnter: () -> Unit = {
        val userName = userNameInput?.value ?: ""
        val password = passwordInput?.value ?: ""
        safeCall { presenter.onEnter(userName, password) }
    }

    private val onEnterClick: MouseEventHandler<HTMLButtonElement> = { emitOnEnter() }

    private val onPasswordKeyDown: KeyboardEventHandler<HTMLDivElement> = { e ->
        if (e.key == "Enter") {
            e.preventDefault()
            emitOnEnter()
        }
    }

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@LoginView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val state = presenter.state
        val loading = state.loading

        // Full-screen gradient background
        Box {
            sx {
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                minHeight = 100.vh
                background = "linear-gradient(180deg, ${ShoppingColors.Primary} 0%, ${ShoppingColors.PrimaryContainer} 100%)".unsafeCast<Background>()
            }

            Card {
                sx {
                    width = 400.px
                    borderRadius = 12.px
                    boxShadow = "0px 2px 8px rgba(0,0,0,0.12)".unsafeCast<BoxShadow>()
                }

                CardContent {
                    sx { padding = 24.px }

                    // Use a form with autocomplete off to prevent all password managers
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
                                backgroundColor = ShoppingColors.Primary.unsafeCast<BackgroundColor>()
                                borderRadius = 18.px
                            }
                            LocalMall { sx { fontSize = 40.px } }
                        }

                        Box {
                            sx {
                                position = Position.relative
                            }
                            Typography {
                                variant = TypographyVariant.h5
                                sx { fontWeight = FontWeight.bold }
                                +"Storefront"
                            }
                            Typography {
                                variant = TypographyVariant.caption
                                sx {
                                    position = Position.absolute
                                    bottom = (-10).px
                                    right = 0.px
                                    color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>()
                                    opacity = number(0.5)
                                    fontSize = 10.px
                                }
                                +"native"
                            }
                        }

                        Typography {
                            variant = TypographyVariant.body2
                            sx { color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>(); textAlign = TextAlign.center }
                            +"Sign in to continue"
                        }

                        // Username
                        TextField {
                            fullWidth = true
                            label = ReactNode("Username")
                            inputRef = userNameRef
                            defaultValue = state.userName ?: ""
                            disabled = loading
                            asDynamic().autoComplete = "off"
                        }

                        // Password
                        TextField {
                            fullWidth = true
                            label = ReactNode("Password")
                            inputRef = passwordRef
                            defaultValue = state.password ?: ""
                            asDynamic().type = "text"
                            asDynamic().autoComplete = "off"
                            asDynamic().InputProps = js("""({
                                inputProps: {
                                    style: { 'WebkitTextSecurity': 'disc', 'textSecurity': 'disc' }
                                }
                            })""")
                            disabled = loading
                            onKeyDown = onPasswordKeyDown
                        }

                        // Error message
                        val errorMessage = state.errorMessage
                        if (!errorMessage.isNullOrBlank()) {
                            Alert {
                                severity = "error"
                                sx { width = 100.pct; borderRadius = 8.px }
                                +errorMessage
                            }
                        }

                        // Login button
                        Button {
                            fullWidth = true
                            variant = ButtonVariant.contained
                            disabled = loading
                            asDynamic().type = "button"
                            onClick = onEnterClick
                            sx {
                                height = 48.px
                                borderRadius = 12.px
                                fontWeight = FontWeight.bold
                                fontSize = 16.px
                                textTransform = None.none
                            }

                            if (loading) {
                                CircularProgress {
                                    size = Size.small
                                    sx { color = NamedColor.white.unsafeCast<Color>() }
                                }
                            } else {
                                +"Sign in"
                            }
                        }
                    }

                    } // form
                }
            }
        }
    }
}
