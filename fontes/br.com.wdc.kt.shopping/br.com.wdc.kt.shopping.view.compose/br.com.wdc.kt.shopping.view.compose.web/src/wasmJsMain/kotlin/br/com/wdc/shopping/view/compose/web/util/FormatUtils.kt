package br.com.wdc.shopping.view.compose.web.util

@JsFun("() => window.location.origin")
private external fun jsOrigin(): JsString

fun productImageUrl(productId: Long): String {
    return "${jsOrigin()}/api/repo/product/$productId/image"
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
