package br.com.wdc.store.nativeui.android.theme

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.TextView

object StoreStyles {

    fun applyCardStyle(view: View, cornerRadius: Float = Dimens.radiusSm.toFloat(), shadow: Boolean = false) {
        val bg = GradientDrawable().apply {
            setColor(StoreColors.Surface)
            this.cornerRadius = cornerRadius * view.resources.displayMetrics.density
        }
        view.background = bg
        if (shadow) {
            view.elevation = 4f * view.resources.displayMetrics.density
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
            view.clipToOutline = true
        }
    }

    fun actionButton(button: Button, bgColor: Int = StoreColors.Primary, textColor: Int = StoreColors.OnPrimary) {
        val density = button.resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = Dimens.radiusMd * density
        }
        button.background = bg
        button.setTextColor(textColor)
        button.isAllCaps = false
        button.stateListAnimator = null
    }

    fun createGradientBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(StoreColors.Primary, StoreColors.PrimaryContainer)
        )
    }

    fun pillBackground(color: Int, radiusDp: Float = 10f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp // density applied at use-site
        }
    }

    fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp
        }
    }

    fun borderPill(strokeColor: Int, radiusDp: Float = 10f, strokeWidth: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(strokeWidth, strokeColor)
            cornerRadius = radiusDp
        }
    }
}
