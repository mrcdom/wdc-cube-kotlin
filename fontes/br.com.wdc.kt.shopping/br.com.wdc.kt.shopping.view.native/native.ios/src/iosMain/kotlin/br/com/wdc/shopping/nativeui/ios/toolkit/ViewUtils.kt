package br.com.wdc.shopping.nativeui.ios.toolkit

import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import platform.UIKit.*

/**
 * Non-theme utility helpers: formatting, network, and image loading.
 */
object ViewUtils {

    /** Base URL for the backend API (set during app initialization). */
    var baseUrl: String = ""

    fun productImageUrl(productId: Long): String {
        val scale = UIScreen.mainScreen.scale.toInt()
        val size = 160 * scale // ~160pt in pixels
        return "$baseUrl/api/repo/product/$productId/image?size=$size"
    }

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
}
