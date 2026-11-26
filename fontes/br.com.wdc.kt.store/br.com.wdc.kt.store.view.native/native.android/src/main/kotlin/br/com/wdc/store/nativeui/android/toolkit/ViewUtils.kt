package br.com.wdc.store.nativeui.android.toolkit

import android.widget.ImageView
import coil.load
import coil.request.CachePolicy
import android.graphics.drawable.ColorDrawable
import br.com.wdc.store.i18n.Messages
import br.com.wdc.store.nativeui.android.theme.StoreColors
import br.com.wdc.store.nativeui.android.views.RootViewAndroid

object ViewUtils {

    var baseUrl: String = ""

    fun productImageUrl(productId: Long): String {
        val density = RootViewAndroid.appContext.resources.displayMetrics.density
        val size = (160 * density).toInt() // ~160dp in pixels
        return "$baseUrl/api/repo/product/$productId/image?size=$size"
    }

    fun formatPrice(value: Double): String {
        val cents = kotlin.math.round(value * 100).toLong()
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign$whole.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** The civil date in UTC of [epochMs], in the language of [messages]. */
    fun formatDate(messages: Messages, epochMs: Long): String {
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
        return messages.date(year = y, month = m + 1, day = d)
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

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

    fun loadImageAsync(imageView: ImageView, url: String) {
        imageView.load(url) {
            crossfade(150)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            placeholder(ColorDrawable(StoreColors.SurfaceVariant))
            error(ColorDrawable(StoreColors.SurfaceVariant))
        }
    }
}
