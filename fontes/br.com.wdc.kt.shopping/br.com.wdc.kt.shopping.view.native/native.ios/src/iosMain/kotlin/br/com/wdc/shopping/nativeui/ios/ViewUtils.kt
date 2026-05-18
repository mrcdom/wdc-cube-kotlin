package br.com.wdc.shopping.nativeui.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGFloat
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
}
