package br.com.wdc.shopping.nativeui.web.util

import kotlinx.browser.window
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun productImageUrl(productId: Long): String {
    return "${window.location.origin}/api/repo/product/$productId/image"
}

fun formatPrice(value: Double): String {
    val cents = kotlin.math.round(value * 100).toLong()
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    return "$sign$whole.${(abs % 100).toString().padStart(2, '0')}"
}

fun stripHtml(html: String): String {
    return html
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<li>"), "\n• ")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("&amp;"), "&")
        .replace(Regex("&lt;"), "<")
        .replace(Regex("&gt;"), ">")
        .replace(Regex("&quot;"), "\"")
        .replace(Regex("&nbsp;"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

/**
 * The purchase date as MM/DD/YYYY, in UTC — the same calendar date the iOS and Android views show. Uses the real
 * calendar: it was approximated with 365-day years and 30-day months, and the dates came out shifted.
 */
fun formatDate(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC).date
    return "${date.month.number.toString().padStart(2, '0')}/${date.day.toString().padStart(2, '0')}/${date.year}"
}
