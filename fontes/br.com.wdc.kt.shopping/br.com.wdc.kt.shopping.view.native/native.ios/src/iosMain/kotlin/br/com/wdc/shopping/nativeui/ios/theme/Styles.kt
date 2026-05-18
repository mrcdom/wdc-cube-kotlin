package br.com.wdc.shopping.nativeui.ios.theme

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.*
import platform.UIKit.*

/**
 * Reusable UI style helpers and component factories for the Shopping app.
 */
@OptIn(ExperimentalForeignApi::class)
object ShoppingStyles {

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
    fun applyCardStyle(view: UIView, cornerRadius: Double = 8.0, shadow: Boolean = false) {
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
            layer.cornerRadius = 12.0
            titleLabel?.font = UIFont.boldSystemFontOfSize(16.0)
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

        // Primary = #1B5E7B (0.106, 0.369, 0.482)
        // PrimaryContainer = #D0E8F2 (0.816, 0.910, 0.949)
        val r1 = 0.106; val g1 = 0.369; val b1 = 0.482
        val r2 = 0.816; val g2 = 0.910; val b2 = 0.949

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
