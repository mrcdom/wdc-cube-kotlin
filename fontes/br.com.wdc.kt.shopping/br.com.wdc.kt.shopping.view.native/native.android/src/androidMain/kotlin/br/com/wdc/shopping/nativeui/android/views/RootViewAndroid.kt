package br.com.wdc.shopping.nativeui.android.views

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.wdc.shopping.nativeui.android.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.shopping.nativeui.android.toolkit.AndroidDom
import br.com.wdc.shopping.presentation.presenter.RootPresenter

class RootViewAndroid(presenter: RootPresenter) : AbstractViewAndroid<RootPresenter>("root-view", presenter) {

    private lateinit var contentSlot: ViewSlot
    private val handler = Handler(Looper.getMainLooper())

    override fun createView(): View = AndroidDom.build(appContext) {
        val root = parent()
        root.setBackgroundColor(ShoppingColors.Primary)

        vStack(configure = {
            (layoutParams as FrameLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }) {
            // Status bar spacer — colored as Primary
            val statusBarSpacer = spacer(height = 0)
            statusBarSpacer.setBackgroundColor(ShoppingColors.Primary)

            // Content below status bar
            val container = frame(configure = {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = 0
                    weight = 1f
                }
            }) {}
            contentSlot = newViewSlot(container)

            // Apply insets to size the status bar spacer
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                statusBarSpacer.layoutParams.height = statusBarHeight
                statusBarSpacer.requestLayout()
                insets
            }
        }
    }

    override fun doUpdate() {
        val state = presenter.state
        contentSlot.sync(state.contentView)

        val errorMessage = state.errorMessage
        if (!errorMessage.isNullOrBlank()) {
            showErrorToast(errorMessage)
            state.errorMessage = null
        }
    }

    private fun showErrorToast(message: String) {
        val root = rootView as? FrameLayout ?: return
        val density = root.resources.displayMetrics.density

        val toast = TextView(root.context).apply {
            text = message
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            val padH = (20 * density).toInt()
            val padV = (12 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ShoppingColors.ToastBackground)
                cornerRadius = 8 * density
            }
        }

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (48 * density).toInt()
            marginStart = (24 * density).toInt()
            marginEnd = (24 * density).toInt()
        }

        root.addView(toast, lp)

        handler.postDelayed({
            toast.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { root.removeView(toast) }
                .start()
        }, 4000)
    }

    companion object {
        lateinit var appContext: Context
            internal set
    }
}
