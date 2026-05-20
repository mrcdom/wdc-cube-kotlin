package br.com.wdc.shopping.nativeui.web.theme

import mui.material.styles.ThemeOptions
import mui.material.styles.createTheme

// Color palette matching compose.web ShoppingTheme
object ShoppingColors {
    const val Primary = "#1B5E7B"
    const val OnPrimary = "#FFFFFF"
    const val PrimaryContainer = "#D0E8F2"
    const val OnPrimaryContainer = "#001F2A"

    const val Secondary = "#4A6572"
    const val OnSecondary = "#FFFFFF"
    const val SecondaryContainer = "#CDE5F0"

    const val Background = "#F5F7FA"
    const val Surface = "#FFFFFF"
    const val OnSurface = "#1A1C1E"
    const val SurfaceVariant = "#E7EBF0"
    const val OnSurfaceVariant = "#42474E"

    const val Error = "#BA1A1A"
    const val ErrorContainer = "#FFDAD6"
    const val OnErrorContainer = "#410002"

    const val Outline = "#72787E"
    const val OutlineVariant = "#C2C7CE"

    const val PriceColor = "#2E7D32"
    const val SuccessColor = "#2E7D32"
    const val SuccessContainer = "#B9F6CA"

    // Derived colors with alpha (hex suffixes)
    const val SurfaceVariant40 = "${SurfaceVariant}66"   // 40% opacity
    const val SurfaceVariant50 = "${SurfaceVariant}80"   // 50% opacity
    const val PriceBackground = "${PriceColor}1A"        // 10% opacity

    // Semi-transparent white overlays (for dark backgrounds)
    const val WhiteOverlay10 = "rgba(255,255,255,0.10)"
    const val WhiteOverlay15 = "rgba(255,255,255,0.15)"
    const val WhiteOverlay20 = "rgba(255,255,255,0.2)"
    const val WhiteOverlay50 = "rgba(255,255,255,0.5)"
    const val WhiteOverlay85 = "rgba(255,255,255,0.85)"
}

/**
 * Reusable inline style objects for MUI Typography overrides via asDynamic().style.
 * These use js("(...)") because it is the only reliable way to override MUI font-weight.
 */
object ShoppingStyles {
    // Font weight overrides for Typography (via asDynamic().style)
    val fontMedium = js("({fontWeight: '500'})")
    val fontNormal = js("({fontWeight: 'normal'})")

    // Title styles (h5 with fontWeight 500, fontSize 28px) — used in CartView, ReceiptView
    val titleH5 = js("({fontWeight: '500', fontSize: '28px'})")

    // Subtitle styles (body2 with fontWeight 500, fontSize 14px)
    val subtitleBody2 = js("({fontWeight: '500', fontSize: '14px'})")

    // Success text (fontWeight 500 + SuccessColor)
    val successText = js("({fontWeight: '500', color: '${ShoppingColors.SuccessColor}'})")

    // Small action button text (normal weight, 12px, no uppercase)
    val smallAction = js("({fontWeight: 'normal', fontSize: '12px', textTransform: 'none'})")
}

val ShoppingTheme = run {
    val opts = js("({})").unsafeCast<ThemeOptions>()
    opts.asDynamic().palette = js("({primary:{main:'${ShoppingColors.Primary}',contrastText:'${ShoppingColors.OnPrimary}'},secondary:{main:'${ShoppingColors.Secondary}',contrastText:'${ShoppingColors.OnSecondary}'},error:{main:'${ShoppingColors.Error}'},background:{'default':'${ShoppingColors.Background}',paper:'${ShoppingColors.Surface}'}})")
    opts.asDynamic().typography = js("({fontFamily:\"'Roboto','Helvetica','Arial',sans-serif\"})")
    createTheme(opts)
}
