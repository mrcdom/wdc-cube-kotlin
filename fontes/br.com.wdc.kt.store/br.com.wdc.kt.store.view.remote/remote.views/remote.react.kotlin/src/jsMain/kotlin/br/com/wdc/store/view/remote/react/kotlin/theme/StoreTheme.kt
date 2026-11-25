package br.com.wdc.store.view.remote.react.kotlin.theme

import mui.material.styles.ThemeOptions
import mui.material.styles.createTheme

/** The Store's palette, the same as the other views'. */
object StoreColors {
    const val Primary = "#1B5E7B"
    const val OnPrimary = "#FFFFFF"
    const val PrimaryContainer = "#D0E8F2"
    const val OnPrimaryContainer = "#001F2A"

    const val Secondary = "#4A6572"
    const val OnSecondary = "#FFFFFF"
    const val SecondaryContainer = "#CDE5F0"

    const val Background = "#F5F7FA"
    const val Surface = "#FFFFFF"
    const val SurfaceVariant = "#E7EBF0"
    const val OnSurfaceVariant = "#42474E"

    const val Error = "#BA1A1A"

    const val PriceColor = "#2E7D32"
    const val SuccessColor = "#2E7D32"
    const val SuccessContainer = "#B9F6CA"

    // Derived colors with alpha
    const val SurfaceVariant40 = "${SurfaceVariant}66"
    const val SurfaceVariant50 = "${SurfaceVariant}80"
    const val PriceBackground = "${PriceColor}1A"

    // White overlays, for the header's dark background
    const val WhiteOverlay10 = "rgba(255,255,255,0.10)"
    const val WhiteOverlay15 = "rgba(255,255,255,0.15)"
    const val WhiteOverlay20 = "rgba(255,255,255,0.2)"
    const val WhiteOverlay85 = "rgba(255,255,255,0.85)"
}

/** Below this width the home shows its panels in tabs. */
const val COMPACT_BREAKPOINT = 480

val StoreTheme = run {
    val options: dynamic = js("({})")
    options.palette = js(
        "({primary:{main:'#1B5E7B',contrastText:'#FFFFFF'},secondary:{main:'#4A6572',contrastText:'#FFFFFF'}," +
            "error:{main:'#BA1A1A'},background:{'default':'#F5F7FA',paper:'#FFFFFF'}})"
    )
    options.typography = js("({fontFamily:\"'Roboto','Helvetica','Arial',sans-serif\"})")
    options.components = js(
        "({MuiButton:{styleOverrides:{root:{textTransform:'none',borderRadius:12}}}," +
            "MuiCard:{styleOverrides:{root:{borderRadius:8}}}})"
    )
    createTheme(options.unsafeCast<ThemeOptions>())
}
