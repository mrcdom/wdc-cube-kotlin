package br.com.wdc.store.nativeui.ios.theme

import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.design.TokenColors
import platform.UIKit.UIColor

/**
 * The Store's color palette for UIKit, built from the shared design tokens ([StoreTokens.Color]).
 */
object StoreColors {
    val Primary = color(StoreTokens.Color.Primary)
    val OnPrimary = color(StoreTokens.Color.OnPrimary)
    val PrimaryContainer = color(StoreTokens.Color.PrimaryContainer)
    val OnPrimaryContainer = color(StoreTokens.Color.OnPrimaryContainer)

    val Secondary = color(StoreTokens.Color.Secondary)
    val SecondaryContainer = color(StoreTokens.Color.SecondaryContainer)

    val Background = color(StoreTokens.Color.Background)
    val Surface = color(StoreTokens.Color.Surface)
    val OnSurface = color(StoreTokens.Color.OnSurface)
    val SurfaceVariant = color(StoreTokens.Color.SurfaceVariant)
    val OnSurfaceVariant = color(StoreTokens.Color.OnSurfaceVariant)

    val Error = color(StoreTokens.Color.Error)
    val PriceColor = color(StoreTokens.Color.Price)
    val SuccessColor = color(StoreTokens.Color.Success)
    val SuccessContainer = color(StoreTokens.Color.SuccessContainer)

    val SurfaceVariant40 = color(StoreTokens.Color.SurfaceVariant40)
    val SurfaceVariant50 = color(StoreTokens.Color.SurfaceVariant50)
    val PriceBackground = color(StoreTokens.Color.PriceBackground)

    val WhiteOverlay10 = color(StoreTokens.Color.WhiteOverlay10)
    val WhiteOverlay15 = color(StoreTokens.Color.WhiteOverlay15)
    val WhiteOverlay20 = color(StoreTokens.Color.WhiteOverlay20)
    val WhiteOverlay50 = color(StoreTokens.Color.WhiteOverlay50)
    val WhiteOverlay85 = color(StoreTokens.Color.WhiteOverlay85)

    val ToastBackground = color(StoreTokens.Color.ToastBackground)

    /** A color token (`0xAARRGGBB`) as a `UIColor`. */
    private fun color(argb: Long): UIColor = UIColor(
        red = TokenColors.red(argb) / 255.0,
        green = TokenColors.green(argb) / 255.0,
        blue = TokenColors.blue(argb) / 255.0,
        alpha = TokenColors.alpha(argb) / 255.0
    )
}
