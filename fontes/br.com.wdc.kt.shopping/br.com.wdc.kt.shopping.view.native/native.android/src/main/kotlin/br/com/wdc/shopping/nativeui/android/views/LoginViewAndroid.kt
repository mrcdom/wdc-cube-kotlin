package br.com.wdc.shopping.nativeui.android.views

import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import br.com.wdc.shopping.nativeui.android.theme.*
import br.com.wdc.shopping.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.shopping.nativeui.android.toolkit.AndroidDom
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter

class LoginViewAndroid(presenter: LoginPresenter) : AbstractViewAndroid<LoginPresenter>("login-view", presenter) {

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
            root.background = ShoppingStyles.createGradientBackground()

            // Centered card
            val card = frame(configure = {
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(ShoppingColors.Surface)
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
                                setColor(ShoppingColors.Primary)
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
                                setImageDrawable(ShoppingIcons.localMall(ctx, 40, android.graphics.Color.WHITE))
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
                            text = "Shopping"
                            textSize = 28f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(ShoppingColors.OnSurface)
                            gravity = Gravity.CENTER
                        }
                        textView {
                            text = "native"
                            textSize = 10f
                            setTextColor(ShoppingColors.OnSurfaceVariant)
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
                        text = "Entre com suas credenciais para continuar"
                        textSize = 14f
                        setTextColor(ShoppingColors.OnSurfaceVariant)
                        gravity = Gravity.CENTER
                    }

                    // Username field
                    userField = editText("Usuário") {
                        inputType = InputType.TYPE_CLASS_TEXT
                        (layoutParams as LinearLayout.LayoutParams).height = (48 * density).toInt()
                    }

                    // Password field
                    passwordField = editText("Senha") {
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
                        setTextColor(ShoppingColors.Error)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        visibility = View.GONE
                    }

                    // Login button
                    loginButton = button("Entrar") {
                        ShoppingStyles.actionButton(this)
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
        val state = presenter.state
        val loading = state.loading
        val error = state.errorMessage

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
            val state = presenter.state
            state.userName = userField.text.toString()
            state.password = passwordField.text.toString()
            presenter.onEnter()
        }
    }
}
