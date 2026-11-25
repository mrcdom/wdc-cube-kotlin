package br.com.wdc.store.nativeui.android.views

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
    private var lastError: String? = ""

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
                    cornerRadius = 12 * density
                }
                background = bg
                elevation = 8 * density
                (layoutParams as FrameLayout.LayoutParams).apply {
                    gravity = Gravity.CENTER
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    marginStart = (24 * density).toInt()
                    marginEnd = (24 * density).toInt()
                }
            }) {
                vStack(spacing = (20 * density).toInt(), configure = {
                    val pad = (32 * density).toInt()
                    val padH = (24 * density).toInt()
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
                                cornerRadius = 18 * density
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
                            text = "WDC Store"
                            textSize = 28f
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
                                topMargin = (32 * density).toInt()
                            }
                        }
                    }

                    // Subtitle
                    textView {
                        text = "Sign in to continue"
                        textSize = 14f
                        setTextColor(StoreColors.OnSurfaceVariant)
                        gravity = Gravity.CENTER
                    }

                    // Username field
                    userField = editText("Username") {
                        inputType = InputType.TYPE_CLASS_TEXT
                        (layoutParams as LinearLayout.LayoutParams).height = (48 * density).toInt()
                    }

                    // Password field
                    passwordField = editText("Password") {
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
                        textSize = 14f
                        gravity = Gravity.CENTER
                        visibility = View.GONE
                    }

                    // Login button
                    loginButton = button("Sign in") {
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
            if (!error.isNullOrBlank()) {
                errorLabel.text = error
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
            scope.onEnter(null, null)
        }
    }
}
