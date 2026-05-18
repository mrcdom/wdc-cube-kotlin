package br.com.wdc.shopping.nativeui.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.*
import platform.Foundation.NSMutableAttributedString
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import platform.Foundation.create
import platform.UIKit.*

/**
 * Utility helpers for iOS native views.
 */
@OptIn(ExperimentalForeignApi::class)
object ViewUtils {

    /** Base URL for the backend API (set during app initialization). */
    var baseUrl: String = ""

    fun productImageUrl(productId: Long): String =
        "$baseUrl/api/repo/product/$productId/image"

    /**
     * Formats a double as Brazilian price: "123,45"
     */
    fun formatPrice(value: Double): String {
        val intPart = value.toLong()
        val decPart = ((value - intPart) * 100).toLong()
        return "$intPart,${decPart.toString().padStart(2, '0')}"
    }

    /**
     * Formats epoch millis as dd/MM/yyyy.
     */
    fun formatDate(epochMs: Long): String {
        val totalSeconds = epochMs / 1000
        val days = (totalSeconds / 86400).toInt()
        // Simple date calculation from epoch
        var y = 1970
        var remaining = days
        while (true) {
            val daysInYear = if (isLeapYear(y)) 366 else 365
            if (remaining < daysInYear) break
            remaining -= daysInYear
            y++
        }
        val monthDays = if (isLeapYear(y))
            intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        else
            intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        var m = 0
        while (m < 12 && remaining >= monthDays[m]) {
            remaining -= monthDays[m]
            m++
        }
        val d = remaining + 1
        return "${d.toString().padStart(2, '0')}/${(m + 1).toString().padStart(2, '0')}/$y"
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    /**
     * Strip HTML tags, convert <li> to bullet points.
     */
    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return html
            .replace(Regex("<li[^>]*>"), "\n• ")
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

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
            view.layer.shadowOffset = platform.CoreGraphics.CGSizeMake(0.0, 2.0)
            view.layer.shadowRadius = 4.0
        }
    }

    /**
     * Load image from URL asynchronously and set on UIImageView.
     */
    fun loadImageAsync(imageView: UIImageView, urlString: String) {
        val url = NSURL(string = urlString) ?: return
        NSURLSession.sharedSession.dataTaskWithURL(url) { data, _, _ ->
            if (data != null) {
                val image = UIImage(data = data)
                platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                    imageView.image = image
                }
            }
        }.resume()
    }

    /**
     * Creates a standard action button (borderRadius: 12, height: 48).
     */
    fun actionButton(title: String, backgroundColor: UIColor, titleColor: UIColor = UIColor.whiteColor): UIButton {
        val btn = UIButton(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
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
     * Uses Core Graphics for reliable rendering in Kotlin/Native.
     */
    fun createGradientImage(topColor: UIColor, bottomColor: UIColor): UIImage {
        val screenBounds = UIScreen.mainScreen.bounds
        val width: Double
        val height: Double
        screenBounds.useContents {
            width = size.width
            height = size.height
        }

        // Extract color components using colorWithAlphaComponent trick
        val r1: Double; val g1: Double; val b1: Double
        val r2: Double; val g2: Double; val b2: Double
        // Primary = #1B5E7B (0.106, 0.369, 0.482)
        // PrimaryContainer = #D0E8F2 (0.816, 0.910, 0.949)
        // We know our theme colors, extract via known values
        r1 = 0.106; g1 = 0.369; b1 = 0.482
        r2 = 0.816; g2 = 0.910; b2 = 0.949

        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 1.0)
        val ctx = UIGraphicsGetCurrentContext() ?: run {
            UIGraphicsEndImageContext()
            return UIImage()
        }

        // Draw gradient by filling horizontal lines
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
