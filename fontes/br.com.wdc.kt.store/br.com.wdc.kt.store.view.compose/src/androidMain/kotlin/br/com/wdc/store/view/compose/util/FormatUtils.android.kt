package br.com.wdc.store.view.compose.util

object PlatformConfig {
    var baseUrl: String = ""
}

actual fun productImageUrl(productId: Long): String {
    return "${PlatformConfig.baseUrl}/api/repo/product/$productId/image"
}
