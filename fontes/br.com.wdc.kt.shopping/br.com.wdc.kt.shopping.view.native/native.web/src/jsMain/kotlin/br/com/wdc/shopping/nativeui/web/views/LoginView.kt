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
import react.dom.events.KeyboardEvent
import react.dom.html.ReactHTML.form
import react.dom.onChange
import react.useEffect
import react.useState
import web.cssom.*
import web.html.HTMLInputElement

class LoginView(private val presenter: LoginPresenter) : ReactCubeView("login-view", presenter) {

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@LoginView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val state = presenter.state
        val loading = state.loading
        var userName by useState(state.userName ?: "")
        var password by useState(state.password ?: "")

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
                                +"Shopping"
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
                            +"Entre com suas credenciais para continuar"
                        }

                        // Username
                        TextField {
                            fullWidth = true
                            label = ReactNode("Usuário")
                            value = userName
                            disabled = loading
                            asDynamic().autoComplete = "off"
                            onChange = { e ->
                                val v = (e.target as HTMLInputElement).value
                                userName = v
                                state.userName = v
                            }
                        }

                        // Password
                        TextField {
                            fullWidth = true
                            label = ReactNode("Senha")
                            value = password
                            asDynamic().type = "text"
                            asDynamic().autoComplete = "off"
                            asDynamic().InputProps = js("""({
                                inputProps: {
                                    style: { 'WebkitTextSecurity': 'disc', 'textSecurity': 'disc' }
                                }
                            })""")
                            disabled = loading
                            onChange = { e ->
                                val v = (e.target as HTMLInputElement).value
                                password = v
                                state.password = v
                            }
                            onKeyDown = { e: KeyboardEvent<*> ->
                                if (e.key == "Enter") {
                                    safeCall { presenter.onEnter() }
                                }
                            }
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
                            onClick = { safeCall { presenter.onEnter() } }
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
                                +"Entrar"
                            }
                        }
                    }

                    } // form
                }
            }
        }
    }
}
