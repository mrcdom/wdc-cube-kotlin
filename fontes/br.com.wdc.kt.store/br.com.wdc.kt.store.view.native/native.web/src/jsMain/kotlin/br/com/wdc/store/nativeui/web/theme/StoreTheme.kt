package br.com.wdc.store.nativeui.web.theme

import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.design.TokenColors
import mui.material.styles.ThemeOptions
import mui.material.styles.createTheme

/** The Store's palette, read from the design tokens (`:store-design`) as CSS colors. */
object StoreColors {
    val Primary = TokenColors.css(StoreTokens.Color.Primary)
    val OnPrimary = TokenColors.css(StoreTokens.Color.OnPrimary)
    val PrimaryContainer = TokenColors.css(StoreTokens.Color.PrimaryContainer)
    val OnPrimaryContainer = TokenColors.css(StoreTokens.Color.OnPrimaryContainer)

    val Secondary = TokenColors.css(StoreTokens.Color.Secondary)
    val OnSecondary = TokenColors.css(StoreTokens.Color.OnSecondary)
    val SecondaryContainer = TokenColors.css(StoreTokens.Color.SecondaryContainer)

    val Background = TokenColors.css(StoreTokens.Color.Background)
    val Surface = TokenColors.css(StoreTokens.Color.Surface)
    val OnSurface = TokenColors.css(StoreTokens.Color.OnSurface)
    val SurfaceVariant = TokenColors.css(StoreTokens.Color.SurfaceVariant)
    val OnSurfaceVariant = TokenColors.css(StoreTokens.Color.OnSurfaceVariant)

    val Error = TokenColors.css(StoreTokens.Color.Error)
    val ErrorContainer = TokenColors.css(StoreTokens.Color.ErrorContainer)
    val OnErrorContainer = TokenColors.css(StoreTokens.Color.OnErrorContainer)

    val Outline = TokenColors.css(StoreTokens.Color.Outline)
    val OutlineVariant = TokenColors.css(StoreTokens.Color.OutlineVariant)

    val PriceColor = TokenColors.css(StoreTokens.Color.Price)
    val SuccessColor = TokenColors.css(StoreTokens.Color.Success)
    val SuccessContainer = TokenColors.css(StoreTokens.Color.SuccessContainer)

    // Colors with alpha
    val SurfaceVariant40 = TokenColors.css(StoreTokens.Color.SurfaceVariant40)
    val SurfaceVariant50 = TokenColors.css(StoreTokens.Color.SurfaceVariant50)
    val PriceBackground = TokenColors.css(StoreTokens.Color.PriceBackground)

    // Semi-transparent white overlays (for dark backgrounds)
    val WhiteOverlay10 = TokenColors.css(StoreTokens.Color.WhiteOverlay10)
    val WhiteOverlay15 = TokenColors.css(StoreTokens.Color.WhiteOverlay15)
    val WhiteOverlay20 = TokenColors.css(StoreTokens.Color.WhiteOverlay20)
    val WhiteOverlay50 = TokenColors.css(StoreTokens.Color.WhiteOverlay50)
    val WhiteOverlay85 = TokenColors.css(StoreTokens.Color.WhiteOverlay85)
}

/** A plain JavaScript object with the given properties (`js("...")` only takes constant text). */
internal fun jsObject(vararg properties: Pair<String, Any?>): dynamic {
    val result: dynamic = js("({})")
    for ((name, value) in properties) result[name] = value
    return result
}

/**
 * Reusable inline style objects for MUI Typography overrides via asDynamic().style.
 * Plain style objects, because that is the only reliable way to override MUI font-weight.
 */
object StoreStyles {
    // Font weight overrides for Typography (via asDynamic().style)
    val fontMedium = jsObject("fontWeight" to "${StoreTokens.FontWeight.Medium}")
    val fontNormal = jsObject("fontWeight" to "normal")

    // Title styles (h5, medium weight, display size) — used in CartView, ReceiptView
    val titleH5 = jsObject(
        "fontWeight" to "${StoreTokens.FontWeight.Medium}",
        "fontSize" to "${StoreTokens.FontSize.Display}px",
    )

    // Subtitle styles (body2, medium weight, body size)
    val subtitleBody2 = jsObject(
        "fontWeight" to "${StoreTokens.FontWeight.Medium}",
        "fontSize" to "${StoreTokens.FontSize.Body}px",
    )

    // Success text (medium weight + SuccessColor)
    val successText = jsObject(
        "fontWeight" to "${StoreTokens.FontWeight.Medium}",
        "color" to StoreColors.SuccessColor,
    )

    // Small action button text (normal weight, caption size, no uppercase)
    val smallAction = jsObject(
        "fontWeight" to "normal",
        "fontSize" to "${StoreTokens.FontSize.Caption}px",
        "textTransform" to "none",
    )
}

val StoreTheme = run {
    val opts = js("({})").unsafeCast<ThemeOptions>()
    opts.asDynamic().palette = jsObject(
        "primary" to jsObject("main" to StoreColors.Primary, "contrastText" to StoreColors.OnPrimary),
        "secondary" to jsObject("main" to StoreColors.Secondary, "contrastText" to StoreColors.OnSecondary),
        "error" to jsObject("main" to StoreColors.Error),
        "background" to jsObject("default" to StoreColors.Background, "paper" to StoreColors.Surface),
    )
    opts.asDynamic().typography = jsObject("fontFamily" to TokenColors.cssFamily(StoreTokens.FontFamily.Base))
    createTheme(opts)
}
