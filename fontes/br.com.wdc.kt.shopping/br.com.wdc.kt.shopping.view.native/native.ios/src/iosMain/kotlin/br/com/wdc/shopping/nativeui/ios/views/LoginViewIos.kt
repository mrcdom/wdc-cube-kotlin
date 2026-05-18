package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.*
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Login screen — gradient background, centered card with logo, fields, and button.
 * Matches the web reference exactly.
 */
@OptIn(ExperimentalForeignApi::class)
class LoginViewIos(presenter: LoginPresenter) : AbstractViewIos<LoginPresenter>("login-view", presenter) {

    // UI references for doUpdate()
    private lateinit var userField: UITextField
    private lateinit var passwordField: UITextField
    private lateinit var errorLabel: UILabel
    private lateinit var loginButton: UIButton
    private lateinit var loadingIndicator: UIActivityIndicatorView

    // Change guards
    private var lastLoading: Boolean? = null
    private var lastError: String? = ""

    private val actions = LoginActions(this).also { retainForGC(it) }

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = ShoppingColors.Primary

        // Gradient simulation: top and bottom halves
        val gradientTop = view(configure = {
            backgroundColor = ShoppingColors.Primary
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                heightAnchor.constraintEqualToAnchor(root.heightAnchor, 0.5)
            ))
        })
        val gradientBottom = view(configure = {
            backgroundColor = ShoppingColors.PrimaryContainer
            NSLayoutConstraint.activateConstraints(listOf(
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                heightAnchor.constraintEqualToAnchor(root.heightAnchor, 0.5)
            ))
        })

        // White card
        val card = view(configure = {
            backgroundColor = UIColor.whiteColor
            layer.cornerRadius = 12.0
            layer.shadowColor = UIColor.blackColor.CGColor
            layer.shadowOpacity = 0.12f
            layer.shadowOffset = CGSizeMake(0.0, 2.0)
            layer.shadowRadius = 8.0
            clipsToBounds = false
            NSLayoutConstraint.activateConstraints(listOf(
                centerXAnchor.constraintEqualToAnchor(root.centerXAnchor),
                centerYAnchor.constraintEqualToAnchor(root.centerYAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 24.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -24.0)
            ))
        }) {
            val stack = vStack(spacing = 20.0, configure = {
                layoutMarginsRelativeArrangement = true
                layoutMargins = UIEdgeInsetsMake(32.0, 24.0, 32.0, 24.0)
            }) {
                // Logo: centered rounded square with emoji
                view {
                    val logo = view(configure = {
                        backgroundColor = ShoppingColors.Primary
                        layer.cornerRadius = 18.0
                        clipsToBounds = true
                    }) {
                        label {
                            text = "\uD83D\uDC5C"
                            font = UIFont.systemFontOfSize(32.0)
                            textAlignment = UIK.TextAlignCenter
                        }.also { center(it, inside = parent()) }
                    }
                    size(logo, width = 72.0, height = 72.0)
                    center(logo)
                    size(parent(), height = 72.0)
                }

                // Title
                label {
                    text = "Shopping"
                    font = UIFont.boldSystemFontOfSize(24.0)
                    textColor = ShoppingColors.OnSurface
                    textAlignment = UIK.TextAlignCenter
                }

                // Subtitle
                label {
                    text = "Entre com suas credenciais para continuar"
                    font = UIFont.systemFontOfSize(14.0)
                    textColor = ShoppingColors.OnSurfaceVariant
                    textAlignment = UIK.TextAlignCenter
                    numberOfLines = 0
                }

                // Username field
                userField = textField("Usu\u00E1rio") {
                    autocorrectionType = UIK.AutocorrectionNo
                    autocapitalizationType = UIK.AutocapNone
                    textContentType = null
                    heightAnchor.constraintEqualToConstant(48.0).active = true
                }

                // Password field
                passwordField = textField("Senha") {
                    secureTextEntry = true
                    autocorrectionType = UIK.AutocorrectionNo
                    autocapitalizationType = UIK.AutocapNone
                    textContentType = null
                    returnKeyType = UIK.ReturnKeyGo
                    delegate = actions
                    heightAnchor.constraintEqualToConstant(48.0).active = true
                }

                // Error label (hidden)
                errorLabel = label {
                    textColor = ShoppingColors.Error
                    font = UIFont.systemFontOfSize(14.0)
                    numberOfLines = 0
                    textAlignment = UIK.TextAlignCenter
                    hidden = true
                }

                // Login button
                loginButton = button("Entrar") {
                    setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                    backgroundColor = ShoppingColors.Primary
                    layer.cornerRadius = 12.0
                    titleLabel?.font = UIFont.boldSystemFontOfSize(16.0)
                    heightAnchor.constraintEqualToConstant(48.0).active = true
                    addTarget(actions, action = sel_registerName("onLoginTapped"), forControlEvents = UIControlEventTouchUpInside)
                }

                // Loading indicator
                loadingIndicator = activityIndicator {
                    color = ShoppingColors.Primary
                }
            }
            pin(stack)
        }
    }

    override fun doUpdate() {
        val state = presenter.state
        val loading = state.loading
        val error = state.errorMessage

        // Guard: loading state
        if (loading != lastLoading) {
            lastLoading = loading
            loginButton.enabled = !loading
            loginButton.alpha = if (loading) 0.6 else 1.0
            userField.enabled = !loading
            passwordField.enabled = !loading
            if (loading) loadingIndicator.startAnimating() else loadingIndicator.stopAnimating()
        }

        // Guard: error message
        if (error != lastError) {
            lastError = error
            if (!error.isNullOrBlank()) {
                errorLabel.text = error
                errorLabel.hidden = false
            } else {
                errorLabel.hidden = true
            }
        }
    }

    internal fun performLogin() {
        safeAction("login") {
            val state = presenter.state
            state.userName = userField.text ?: ""
            state.password = passwordField.text ?: ""
            presenter.onEnter()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class LoginActions(private val view: LoginViewIos) : NSObject(), UITextFieldDelegateProtocol {
    @ObjCAction
    fun onLoginTapped() {
        view.performLogin()
    }

    override fun textFieldShouldReturn(textField: UITextField): Boolean {
        view.performLogin()
        return true
    }
}
