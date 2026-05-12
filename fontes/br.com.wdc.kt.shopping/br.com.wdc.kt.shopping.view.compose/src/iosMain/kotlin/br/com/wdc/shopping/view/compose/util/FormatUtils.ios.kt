package br.com.wdc.shopping.view.compose.util

/**
 * Configurable base URL for iOS.
 * Must be set during app initialization before any view renders.
 */
object PlatformConfig {
    var baseUrl: String = ""
}

actual fun productImageUrl(productId: Long): String {
    return "${PlatformConfig.baseUrl}/api/repo/product/$productId/image"
}
