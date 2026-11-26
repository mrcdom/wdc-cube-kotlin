package br.com.wdc.store.nativeui.web.util

import br.com.wdc.store.i18n.Messages
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
 * The purchase date in the language's format (MM/DD/YYYY in en-US, DD/MM/YYYY in pt-BR), in UTC — the same calendar
 * date the iOS and Android views show. Uses the real calendar: it was approximated with 365-day years and 30-day
 * months, and the dates came out shifted.
 */
fun formatDate(epochMs: Long, messages: Messages = pageMessages()): String {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC).date
    return messages.date(date.year, date.month.number, date.day)
}

private fun pageMessages(): Messages = messages
