package br.com.wdc.store.nativeui.android.views

import br.com.wdc.store.i18n.commonAppName
import br.com.wdc.store.i18n.loginPassword
import br.com.wdc.store.i18n.loginSignIn
import br.com.wdc.store.i18n.loginSubtitle
import br.com.wdc.store.i18n.loginUsername
import br.com.wdc.store.presentation.i18n.Message
import br.com.wdc.store.design.StoreTokens
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import br.com.wdc.store.nativeui.android.theme.*
import br.com.wdc.store.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.store.nativeui.android.toolkit.AndroidDom
import br.com.wdc.store.nativeui.android.AndroidNativeRootPresenter
import br.com.wdc.store.presentation.presenter.open.login.LoginScope

class LoginViewAndroid(app: AndroidNativeRootPresenter, scope: LoginScope) : AbstractViewAndroid<LoginScope>("login-view", app, scope) {

    private lateinit var userField: EditText
    private lateinit var passwordField: EditText
    private lateinit var errorLabel: TextView
    private lateinit var loginButton: Button
    private lateinit var loadingIndicator: ProgressBar

    private var lastLoading: Boolean? = null
    private var lastError: Message? = null

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val root = parent()
            root.background = StoreStyles.createGradientBackground()

            // Centered card
            val card = frame(configure = {
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(StoreColors.Surface)
                    cornerRadius = Dimens.radiusMd * density
                }
                background = bg
                elevation = 8 * density
                (layoutParams as FrameLayout.LayoutParams).apply {
                    gravity = Gravity.CENTER
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    marginStart = (Dimens.spacingXl * density).toInt()
                    marginEnd = (Dimens.spacingXl * density).toInt()
                }
            }) {
                vStack(spacing = (20 * density).toInt(), configure = {
                    val pad = (Dimens.spacingXxl * density).toInt()
                    val padH = (Dimens.spacingXl * density).toInt()
                    setPadding(padH, pad, padH, pad)
                }) {
                    // Logo
                    frame(configure = {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                            height = (72 * density).toInt()
                            gravity = Gravity.CENTER
                        }
                    }) {
                        frame(configure = {
                            val bg = android.graphics.drawable.GradientDrawable().apply {
                                setColor(StoreColors.Primary)
                                cornerRadius = Dimens.logoCornerRadius * density
                            }
                            background = bg
                            (layoutParams as FrameLayout.LayoutParams).apply {
                                width = (72 * density).toInt()
                                height = (72 * density).toInt()
                                gravity = Gravity.CENTER
                            }
                        }) {
                            imageView {
                                setImageDrawable(StoreIcons.localMall(ctx, 40, android.graphics.Color.WHITE))
                                (layoutParams as FrameLayout.LayoutParams).apply {
                                    width = (40 * density).toInt()
                                    height = (40 * density).toInt()
                                    gravity = Gravity.CENTER
                                }
                            }
                        }
                    }

                    // Title
                    frame(configure = {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                    }) {
                        textView {
                            text = messages.commonAppName()
                            textSize = StoreTokens.FontSize.Display.toFloat()
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(StoreColors.OnSurface)
                            gravity = Gravity.CENTER
                        }
                        textView {
                            text = "native"
                            textSize = 10f
                            setTextColor(StoreColors.OnSurfaceVariant)
                            alpha = 0.5f
                            (layoutParams as FrameLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                gravity = Gravity.BOTTOM or Gravity.END
                                topMargin = (Dimens.spacingXxl * density).toInt()
                            }
                        }
                    }

                    // Subtitle
                    textView {
                        text = messages.loginSubtitle()
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        setTextColor(StoreColors.OnSurfaceVariant)
                        gravity = Gravity.CENTER
                    }

                    // Username field
                    userField = editText(messages.loginUsername()) {
                        inputType = InputType.TYPE_CLASS_TEXT
                        (layoutParams as LinearLayout.LayoutParams).height = (48 * density).toInt()
                    }

                    // Password field
                    passwordField = editText(messages.loginPassword()) {
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        imeOptions = EditorInfo.IME_ACTION_GO
                        (layoutParams as LinearLayout.LayoutParams).height = (48 * density).toInt()
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                                performLogin()
                            }
                            true
                        }
                    }

                    // Error label
                    errorLabel = textView {
                        setTextColor(StoreColors.Error)
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        gravity = Gravity.CENTER
                        visibility = View.GONE
                    }

                    // Login button
                    loginButton = button(messages.loginSignIn()) {
                        StoreStyles.actionButton(this)
                        (layoutParams as LinearLayout.LayoutParams).height = (48 * density).toInt()
                        setOnClickListener { performLogin() }
                    }

                    // Loading indicator
                    loadingIndicator = progressBar {
                        visibility = View.GONE
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                    }
                }
            }

            // Limit card max width
            val maxW = (Dimens.cardMaxWidth * density).toInt()
            root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                val cardView = (root as FrameLayout).getChildAt(0)
                val available = right - left
                if (available > maxW + (48 * density).toInt()) {
                    (cardView.layoutParams as FrameLayout.LayoutParams).apply {
                        width = maxW
                    }
                    cardView.requestLayout()
                }
            }
        }
    }

    override fun doUpdate() {
        val loading = scope.loading
        val error = scope.errorMessage

        if (loading != lastLoading) {
            lastLoading = loading
            loginButton.isEnabled = !loading
            loginButton.alpha = if (loading) 0.6f else 1.0f
            userField.isEnabled = !loading
            passwordField.isEnabled = !loading
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        }

        if (error != lastError) {
            lastError = error
            if (error != null) {
                errorLabel.text = translate(error)
                errorLabel.visibility = View.VISIBLE
            } else {
                errorLabel.visibility = View.GONE
            }
        }
    }

    private fun performLogin() {
        safeAction("login") {
            scope.userName = userField.text.toString()
            scope.password = passwordField.text.toString()
            scope.onEnter()
        }
    }
}
