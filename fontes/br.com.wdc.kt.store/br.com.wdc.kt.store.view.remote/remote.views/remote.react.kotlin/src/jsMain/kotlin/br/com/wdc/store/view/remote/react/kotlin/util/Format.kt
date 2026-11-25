package br.com.wdc.store.view.remote.react.kotlin.util

import kotlin.js.Date

/**
 * The image of a product. The host serves it at the root (`/image/product/<id>.png`), whatever context served this
 * page, so the path is absolute (the TypeScript client, served at the root, uses `./image/...`).
 */
fun productImagePath(productId: Any?): String = "/image/product/$productId.png"

/** A money amount as `1,234.50` (US English, at least two decimals), without the currency sign. */
fun formatPrice(value: Double?): String {
    if (value == null || value.isNaN()) return ""
    return value.asDynamic().toLocaleString("en-US", js("({minimumFractionDigits: 2})")) as String
}

/** A date sent as epoch milliseconds, as MM/DD/YYYY: the calendar date in UTC, as the other views show it. */
fun formatDate(epochMs: Double?): String {
    if (epochMs == null) return "Date unavailable"
    val date = Date(epochMs)
    val month = (date.getUTCMonth() + 1).toString().padStart(2, '0')
    val day = date.getUTCDate().toString().padStart(2, '0')
    return "$month/$day/${date.getUTCFullYear()}"
}

/** A product description (HTML) as plain text: line breaks and bullets kept. */
fun stripHtml(html: String): String = html
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<li>", RegexOption.IGNORE_CASE), "\n• ")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&nbsp;", " ")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
