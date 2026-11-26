package br.com.wdc.store.view.remote.react.kotlin.theme

import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.design.TokenColors
import mui.material.styles.ThemeOptions
import mui.material.styles.createTheme

/** The Store's palette, the same as the other views', read from the design tokens (`:store-design`). */
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
    val SurfaceVariant = TokenColors.css(StoreTokens.Color.SurfaceVariant)
    val OnSurfaceVariant = TokenColors.css(StoreTokens.Color.OnSurfaceVariant)

    val Error = TokenColors.css(StoreTokens.Color.Error)

    val PriceColor = TokenColors.css(StoreTokens.Color.Price)
    val SuccessColor = TokenColors.css(StoreTokens.Color.Success)
    val SuccessContainer = TokenColors.css(StoreTokens.Color.SuccessContainer)

    // Colors with alpha
    val SurfaceVariant40 = TokenColors.css(StoreTokens.Color.SurfaceVariant40)
    val SurfaceVariant50 = TokenColors.css(StoreTokens.Color.SurfaceVariant50)
    val PriceBackground = TokenColors.css(StoreTokens.Color.PriceBackground)

    // White overlays, for the header's dark background
    val WhiteOverlay10 = TokenColors.css(StoreTokens.Color.WhiteOverlay10)
    val WhiteOverlay15 = TokenColors.css(StoreTokens.Color.WhiteOverlay15)
    val WhiteOverlay20 = TokenColors.css(StoreTokens.Color.WhiteOverlay20)
    val WhiteOverlay85 = TokenColors.css(StoreTokens.Color.WhiteOverlay85)
}

/** Below this width the home shows its panels in tabs. */
const val COMPACT_BREAKPOINT = 480

/** A plain JavaScript object with the given properties (`js("...")` only takes constant text). */
internal fun jsObject(vararg properties: Pair<String, Any?>): dynamic {
    val result: dynamic = js("({})")
    for ((name, value) in properties) result[name] = value
    return result
}

val StoreTheme = run {
    val options: dynamic = js("({})")
    options.palette = jsObject(
        "primary" to jsObject("main" to StoreColors.Primary, "contrastText" to StoreColors.OnPrimary),
        "secondary" to jsObject("main" to StoreColors.Secondary, "contrastText" to StoreColors.OnSecondary),
        "error" to jsObject("main" to StoreColors.Error),
        "background" to jsObject("default" to StoreColors.Background, "paper" to StoreColors.Surface),
    )
    options.typography = jsObject("fontFamily" to TokenColors.cssFamily(StoreTokens.FontFamily.Base))
    options.components = jsObject(
        "MuiButton" to jsObject(
            "styleOverrides" to jsObject(
                "root" to jsObject("textTransform" to "none", "borderRadius" to StoreTokens.Radius.Md),
            ),
        ),
        "MuiCard" to jsObject(
            "styleOverrides" to jsObject("root" to jsObject("borderRadius" to StoreTokens.Radius.Sm)),
        ),
    )
    createTheme(options.unsafeCast<ThemeOptions>())
}
