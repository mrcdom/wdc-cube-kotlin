package br.com.wdc.store.nativeui.ios.views

import br.com.wdc.store.design.StoreTokens.FontSize
import br.com.wdc.store.design.StoreTokens.Radius
import br.com.wdc.store.design.StoreTokens.Spacing
import br.com.wdc.store.nativeui.ios.theme.pt
import br.com.wdc.store.nativeui.ios.toolkit.*
import br.com.wdc.store.nativeui.ios.theme.*
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.nativeui.ios.IosNativeRootPresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.CoreGraphics.CGRectInset
import platform.CoreGraphics.CGRectUnion
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Login screen — gradient background, centered card with logo, fields, and button.
 * Matches the web reference exactly.
 *
 * The keyboard never hides what the user needs: the card sits in a scroll view that ends where the keyboard begins,
 * and the field being edited — with the button below it — is scrolled into sight. Return on the user name goes to the
 * password; return on the password signs in. A tap outside the fields, or a drag of the card, closes the keyboard.
 */
@OptIn(ExperimentalForeignApi::class)
class LoginViewIos(app: IosNativeRootPresenter, scope: LoginScope) : AbstractViewIos<LoginScope>("login-view", app, scope) {

    // UI references for doUpdate()
    private lateinit var userField: UITextField
    private lateinit var passwordField: UITextField
    private lateinit var errorLabel: UILabel
    private lateinit var loginButton: UIButton
    private lateinit var loadingIndicator: UIActivityIndicatorView
    private lateinit var scroll: UIScrollView
    private var keyboardObserver: Any? = null

    // Change guards
    private var lastLoading: Boolean? = null
    private var lastError: String? = ""

    private val actions = LoginActions(this).also { retainForGC(it) }

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = StoreColors.Primary

        // Gradient background (Primary → PrimaryContainer, top to bottom)
        val gradientBg = imageView {
            image = StoreStyles.createGradientImage(
                StoreColors.Primary, StoreColors.PrimaryContainer
            )
            contentMode = UIK.ContentModeScaleToFill
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }

        // The card scrolls, in a scroll view that ends at the keyboard
        scroll = scrollView(configure = {
            keyboardDismissMode = UIScrollViewKeyboardDismissMode.UIScrollViewKeyboardDismissModeInteractive
            alwaysBounceVertical = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.safeAreaLayoutGuide.topAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.keyboardLayoutGuide.topAnchor)
            ))
        }) {
            val sv = parent() as UIScrollView
            // At least as tall as the visible area, so the card stays centered; taller when the card does not fit
            view(configure = {
                val fillVisibleArea = heightAnchor.constraintEqualToAnchor(sv.frameLayoutGuide.heightAnchor)
                fillVisibleArea.priority = 250.0f
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(sv.contentLayoutGuide.topAnchor),
                    bottomAnchor.constraintEqualToAnchor(sv.contentLayoutGuide.bottomAnchor),
                    leadingAnchor.constraintEqualToAnchor(sv.contentLayoutGuide.leadingAnchor),
                    trailingAnchor.constraintEqualToAnchor(sv.contentLayoutGuide.trailingAnchor),
                    widthAnchor.constraintEqualToAnchor(sv.frameLayoutGuide.widthAnchor),
                    heightAnchor.constraintGreaterThanOrEqualToAnchor(sv.frameLayoutGuide.heightAnchor),
                    fillVisibleArea
                ))
            }) {
                val content = parent()

                // White card
                view(configure = {
                    backgroundColor = UIColor.whiteColor
                    layer.cornerRadius = Radius.Md.pt
                    layer.shadowColor = UIColor.blackColor.CGColor
                    layer.shadowOpacity = 0.12f
                    layer.shadowOffset = CGSizeMake(0.0, 2.0)
                    layer.shadowRadius = 8.0
                    clipsToBounds = false
                    val preferredWidth = widthAnchor.constraintEqualToConstant(560.0)
                    preferredWidth.priority = 750.0f
                    NSLayoutConstraint.activateConstraints(listOf(
                        centerXAnchor.constraintEqualToAnchor(content.centerXAnchor),
                        centerYAnchor.constraintEqualToAnchor(content.centerYAnchor),
                        topAnchor.constraintGreaterThanOrEqualToAnchor(content.topAnchor, Spacing.Xl.pt),
                        bottomAnchor.constraintLessThanOrEqualToAnchor(content.bottomAnchor, -Spacing.Xl.pt),
                        leadingAnchor.constraintGreaterThanOrEqualToAnchor(content.leadingAnchor, Spacing.Xl.pt),
                        trailingAnchor.constraintLessThanOrEqualToAnchor(content.trailingAnchor, -Spacing.Xl.pt),
                        widthAnchor.constraintLessThanOrEqualToConstant(560.0),
                        preferredWidth
                    ))
                }) {
                    val stack = vStack(spacing = 20.0, configure = {
                        layoutMarginsRelativeArrangement = true
                        layoutMargins = UIEdgeInsetsMake(Spacing.Xxl.pt, Spacing.Xl.pt, Spacing.Xxl.pt, Spacing.Xl.pt)
                    }) {
                        // Logo: centered rounded square with LocalMall SVG icon
                        view {
                            val logo = view(configure = {
                                backgroundColor = StoreColors.Primary
                                layer.cornerRadius = Radius.Lg.pt
                                clipsToBounds = true
                            }) {
                                val iconIv = imageView {
                                    image = StoreIcons.localMall(40.0, UIColor.whiteColor)
                                    contentMode = UIK.ContentModeScaleAspectFit
                                }
                                center(iconIv, inside = parent())
                                size(iconIv, width = 40.0, height = 40.0)
                            }
                            size(logo, width = 72.0, height = 72.0)
                            center(logo)
                            size(parent(), height = 72.0)
                        }

                        // Title
                        label {
                            text = "WDC Store"
                            font = UIFont.boldSystemFontOfSize(FontSize.Display.pt)
                            textColor = StoreColors.OnSurface
                            textAlignment = UIK.TextAlignCenter
                        }

                        // Subtitle
                        label {
                            text = "Sign in to continue"
                            font = UIFont.systemFontOfSize(FontSize.Body.pt)
                            textColor = StoreColors.OnSurfaceVariant
                            textAlignment = UIK.TextAlignCenter
                            numberOfLines = 0
                        }

                        // Username field
                        userField = textField("Username") {
                            autocorrectionType = UIK.AutocorrectionNo
                            autocapitalizationType = UIK.AutocapNone
                            // what the field is, so the system's password AutoFill can fill it
                            textContentType = UITextContentTypeUsername
                            returnKeyType = UIK.ReturnKeyNext
                            delegate = actions
                            heightAnchor.constraintEqualToConstant(48.0).active = true
                        }

                        // Password field
                        passwordField = textField("Password") {
                            secureTextEntry = true
                            autocorrectionType = UIK.AutocorrectionNo
                            autocapitalizationType = UIK.AutocapNone
                            textContentType = UITextContentTypePassword
                            returnKeyType = UIK.ReturnKeyGo
                            delegate = actions
                            heightAnchor.constraintEqualToConstant(48.0).active = true
                        }

                        // Error label (hidden)
                        errorLabel = label {
                            textColor = StoreColors.Error
                            font = UIFont.systemFontOfSize(FontSize.Body.pt)
                            numberOfLines = 0
                            textAlignment = UIK.TextAlignCenter
                            hidden = true
                        }

                        // Login button
                        loginButton = button("Sign in") {
                            setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                            backgroundColor = StoreColors.Primary
                            layer.cornerRadius = Radius.Md.pt
                            titleLabel?.font = UIFont.boldSystemFontOfSize(FontSize.BodyLarge.pt)
                            heightAnchor.constraintEqualToConstant(48.0).active = true
                            addTarget(actions, action = sel_registerName("onLoginTapped"), forControlEvents = UIControlEventTouchUpInside)
                        }

                        // Loading indicator
                        loadingIndicator = activityIndicator {
                            color = StoreColors.Primary
                        }
                    }
                    pin(stack)
                }
            }
        }

        // A tap outside the fields closes the keyboard; the tap still reaches what it touched
        root.addGestureRecognizer(
            UITapGestureRecognizer(target = actions, action = sel_registerName("onBackgroundTapped"))
                .apply { cancelsTouchesInView = false }
                .also { retainForGC(it) }
        )

        // The keyboard is up: the field being edited comes into sight above it
        keyboardObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIKeyboardDidShowNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ -> revealEditedField() }
    }

    override fun onRelease() {
        keyboardObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        keyboardObserver = null
    }

    override fun doUpdate() {
        val loading = scope.loading
        val error = scope.errorMessage

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

    /** Return on a field: from the user name to the password; on the password, sign in. */
    internal fun onReturn(textField: UITextField) {
        if (textField == userField) {
            passwordField.becomeFirstResponder()
        } else {
            textField.resignFirstResponder()
            performLogin()
        }
    }

    internal fun dismissKeyboard() {
        rootView.endEditing(true)
    }

    /** Scrolls the field being edited into sight — with the button below it, so the user sees where to go next. */
    internal fun revealEditedField() {
        val field = listOf(userField, passwordField).firstOrNull { it.isFirstResponder() } ?: return
        scroll.layoutIfNeeded()
        val area = CGRectUnion(
            field.convertRect(field.bounds, toView = scroll),
            loginButton.convertRect(loginButton.bounds, toView = scroll)
        )
        scroll.scrollRectToVisible(CGRectInset(area, 0.0, -Spacing.Lg.pt), animated = true)
    }

    internal fun performLogin() {
        safeAction("login") {
            scope.userName = userField.text ?: ""
            scope.password = passwordField.text ?: ""
            scope.onEnter()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class LoginActions(private val view: LoginViewIos) : NSObject(), UITextFieldDelegateProtocol {
    @ObjCAction
    fun onLoginTapped() {
        view.performLogin()
    }

    @ObjCAction
    fun onBackgroundTapped() {
        view.dismissKeyboard()
    }

    override fun textFieldShouldReturn(textField: UITextField): Boolean {
        view.onReturn(textField)
        return true
    }

    override fun textFieldDidBeginEditing(textField: UITextField) {
        // when the keyboard is already up (moving from one field to the other), there is no new "did show"
        view.revealEditedField()
    }
}
