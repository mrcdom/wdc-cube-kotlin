package br.com.wdc.shopping.nativeui.web.util

import kotlinx.browser.window

fun productImageUrl(productId: Long): String {
    return "${window.location.origin}/api/repo/product/$productId/image"
}

fun formatPrice(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart,${fracPart.toString().padStart(2, '0')}"
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

fun formatDate(epochMs: Long): String {
    val totalSeconds = epochMs / 1000
    val days = totalSeconds / 86400
    val year = 1970 + (days / 365).toInt()
    val month = ((days % 365) / 30 + 1).toInt().coerceIn(1, 12)
    val day = ((days % 365) % 30 + 1).toInt().coerceIn(1, 31)
    return "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
}
