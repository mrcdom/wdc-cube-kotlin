package br.com.wdc.store.nativeui.android.views

import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.nativeui.android.AndroidNativeRootPresenter
import br.com.wdc.store.presentation.presenter.RootScope
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
import br.com.wdc.store.nativeui.android.theme.Dimens
import br.com.wdc.store.nativeui.android.theme.StoreColors
import br.com.wdc.store.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.store.nativeui.android.toolkit.AndroidDom

class RootViewAndroid(app: AndroidNativeRootPresenter, scope: RootScope) : AbstractViewAndroid<RootScope>("root-view", app, scope) {

    private lateinit var contentSlot: ViewSlot
    private val handler = Handler(Looper.getMainLooper())

    override fun createView(): View = AndroidDom.build(appContext) {
        val root = parent()
        root.setBackgroundColor(StoreColors.Primary)

        vStack(configure = {
            (layoutParams as FrameLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }) {
            // Status bar spacer — colored as Primary
            val statusBarSpacer = spacer(height = 0)
            statusBarSpacer.setBackgroundColor(StoreColors.Primary)

            // Content below status bar
            val container = frame(configure = {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = 0
                    weight = 1f
                }
            }) {}
            contentSlot = newViewSlot(container)

            // Apply insets to size the top spacer (status bar or caption bar in freeform window mode)
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                val topInset = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.captionBar()
                ).top
                statusBarSpacer.layoutParams.height = topInset
                statusBarSpacer.requestLayout()
                insets
            }
        }
    }

    override fun doUpdate() {
        contentSlot.sync(scope.content?.let(::viewOf))

        val errorMessage = scope.errorMessage
        if (errorMessage != null) {
            showErrorToast(translate(errorMessage)!!)
            scope.errorMessage = null
        }
    }

    private fun showErrorToast(message: String) {
        val root = rootView as? FrameLayout ?: return
        val density = root.resources.displayMetrics.density

        val toast = TextView(root.context).apply {
            text = message
            textSize = StoreTokens.FontSize.Body.toFloat()
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            val padH = (20 * density).toInt()
            val padV = (Dimens.spacingMd * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(StoreColors.ToastBackground)
                cornerRadius = Dimens.radiusSm * density
            }
        }

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (48 * density).toInt()
            marginStart = (Dimens.spacingXl * density).toInt()
            marginEnd = (Dimens.spacingXl * density).toInt()
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
