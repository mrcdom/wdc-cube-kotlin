package br.com.wdc.store.view.compose.util

@JsFun("() => window.location.origin")
private external fun jsOrigin(): JsString

actual fun productImageUrl(productId: Long): String {
    return "${jsOrigin()}/api/repo/product/$productId/image"
}
