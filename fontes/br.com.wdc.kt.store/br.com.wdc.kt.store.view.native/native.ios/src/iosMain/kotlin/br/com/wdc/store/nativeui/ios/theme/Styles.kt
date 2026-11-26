package br.com.wdc.store.nativeui.ios.theme

import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.design.StoreTokens.FontSize
import br.com.wdc.store.design.StoreTokens.Radius
import br.com.wdc.store.design.TokenColors
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.*
import platform.UIKit.*

/**
 * Reusable UI style helpers and component factories for the Store app.
 */
@OptIn(ExperimentalForeignApi::class)
object StoreStyles {

    /**
     * Creates a rounded rect UIView with the given color (for avatars/badges).
     */
    fun roundedView(
        size: Double,
        cornerRadius: Double,
        backgroundColor: UIColor
    ): UIView {
        val v = UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            this.backgroundColor = backgroundColor
            layer.cornerRadius = cornerRadius
            clipsToBounds = true
        }
        NSLayoutConstraint.activateConstraints(listOf(
            v.widthAnchor.constraintEqualToConstant(size),
            v.heightAnchor.constraintEqualToConstant(size)
        ))
        return v
    }

    /**
     * Apply card styling to a UIView (rounded corners, white bg, optional shadow).
     */
    fun applyCardStyle(view: UIView, cornerRadius: Double = Radius.Sm.pt, shadow: Boolean = false) {
        view.backgroundColor = UIColor.whiteColor
        view.layer.cornerRadius = cornerRadius
        view.clipsToBounds = !shadow
        if (shadow) {
            view.layer.shadowColor = UIColor.blackColor.CGColor
            view.layer.shadowOpacity = 0.12f
            view.layer.shadowOffset = CGSizeMake(0.0, 2.0)
            view.layer.shadowRadius = 4.0
        }
    }

    /**
     * Creates a standard action button (borderRadius: 12, height: 48).
     */
    fun actionButton(title: String, backgroundColor: UIColor, titleColor: UIColor = UIColor.whiteColor): UIButton {
        val btn = UIButton(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
            translatesAutoresizingMaskIntoConstraints = false
            setTitle(title, forState = UIControlStateNormal)
            setTitleColor(titleColor, forState = UIControlStateNormal)
            this.backgroundColor = backgroundColor
            layer.cornerRadius = Radius.Md.pt
            titleLabel?.font = UIFont.boldSystemFontOfSize(FontSize.BodyLarge.pt)
        }
        btn.heightAnchor.constraintEqualToConstant(48.0).active = true
        return btn
    }

    /**
     * Creates a vertical gradient UIImage (top color → bottom color).
     */
    fun createGradientImage(topColor: UIColor, bottomColor: UIColor): UIImage {
        val screenBounds = UIScreen.mainScreen.bounds
        val width: Double
        val height: Double
        screenBounds.useContents {
            width = size.width
            height = size.height
        }

        // From Primary (top) to PrimaryContainer (bottom)
        val top = StoreTokens.Color.Primary
        val bottom = StoreTokens.Color.PrimaryContainer
        val r1 = TokenColors.red(top) / 255.0; val g1 = TokenColors.green(top) / 255.0; val b1 = TokenColors.blue(top) / 255.0
        val r2 = TokenColors.red(bottom) / 255.0; val g2 = TokenColors.green(bottom) / 255.0; val b2 = TokenColors.blue(bottom) / 255.0

        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 1.0)
        val ctx = UIGraphicsGetCurrentContext() ?: run {
            UIGraphicsEndImageContext()
            return UIImage()
        }

        val steps = height.toInt().coerceAtLeast(1)
        for (i in 0 until steps) {
            val t = i.toDouble() / steps.toDouble()
            CGContextSetRGBFillColor(ctx, r1 + (r2 - r1) * t, g1 + (g2 - g1) * t, b1 + (b2 - b1) * t, 1.0)
            CGContextFillRect(ctx, CGRectMake(0.0, i.toDouble(), width, 1.0))
        }

        val image = UIGraphicsGetImageFromCurrentImageContext() ?: UIImage()
        UIGraphicsEndImageContext()
        return image
    }
}
