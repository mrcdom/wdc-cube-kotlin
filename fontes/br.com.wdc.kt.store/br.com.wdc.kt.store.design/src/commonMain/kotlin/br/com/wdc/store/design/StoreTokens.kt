// Generated from br.com.wdc.kt.store.design/tokens.json by ./gradlew :store-scripts:generateDesignTokens: do not edit.
package br.com.wdc.store.design

/**
 * The WDC Store's design tokens, the same for every view technology. Colors are `0xAARRGGBB` (see [TokenColors]);
 * dimensions are in the platform's unit (dp, pt, px); font weights are CSS weights.
 */
object StoreTokens {
    object Color {
        const val Primary: Long = 0xFF1B5E7BL
        const val OnPrimary: Long = 0xFFFFFFFFL
        const val PrimaryContainer: Long = 0xFFD0E8F2L
        const val OnPrimaryContainer: Long = 0xFF001F2AL
        const val Secondary: Long = 0xFF4A6572L
        const val OnSecondary: Long = 0xFFFFFFFFL
        const val SecondaryContainer: Long = 0xFFCDE5F0L
        const val OnSecondaryContainer: Long = 0xFF051F2CL
        const val Background: Long = 0xFFF5F7FAL
        const val Surface: Long = 0xFFFFFFFFL
        const val OnSurface: Long = 0xFF1A1C1EL
        const val SurfaceVariant: Long = 0xFFE7EBF0L
        const val OnSurfaceVariant: Long = 0xFF42474EL
        const val Error: Long = 0xFFBA1A1AL
        const val OnError: Long = 0xFFFFFFFFL
        const val ErrorContainer: Long = 0xFFFFDAD6L
        const val OnErrorContainer: Long = 0xFF410002L
        const val Outline: Long = 0xFF72787EL
        const val OutlineVariant: Long = 0xFFC2C7CEL
        const val Price: Long = 0xFF2E7D32L
        const val Success: Long = 0xFF2E7D32L
        const val SuccessContainer: Long = 0xFFB9F6CAL
        const val SurfaceVariant40: Long = 0x66E7EBF0L
        const val SurfaceVariant50: Long = 0x80E7EBF0L
        const val PriceBackground: Long = 0x1A2E7D32L
        const val WhiteOverlay10: Long = 0x1AFFFFFFL
        const val WhiteOverlay15: Long = 0x26FFFFFFL
        const val WhiteOverlay20: Long = 0x33FFFFFFL
        const val WhiteOverlay50: Long = 0x80FFFFFFL
        const val WhiteOverlay85: Long = 0xD9FFFFFFL
        const val ToastBackground: Long = 0xE6333333L
    }

    object Spacing {
        const val Xs: Int = 4
        const val Sm: Int = 8
        const val Md: Int = 12
        const val Lg: Int = 16
        const val Xl: Int = 24
        const val Xxl: Int = 32
    }

    object Radius {
        const val Xs: Int = 4
        const val Sm: Int = 8
        const val Md: Int = 12
        const val Lg: Int = 18
        const val Xl: Int = 20
    }

    object FontSize {
        const val Caption: Int = 12
        const val Body: Int = 14
        const val BodyLarge: Int = 16
        const val Title: Int = 20
        const val Headline: Int = 24
        const val Display: Int = 28
    }

    object FontWeight {
        const val Regular: Int = 400
        const val Medium: Int = 500
        const val Bold: Int = 700
    }

    object FontFamily {
        val Base: List<String> = listOf("Roboto", "Helvetica", "Arial", "sans-serif")
    }
}
