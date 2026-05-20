package br.com.wdc.shopping.nativeui.ios.theme

import platform.UIKit.UIColor

/**
 * Design tokens — color palette matching the Shopping app UI spec.
 */
object ShoppingColors {
    val Primary = UIColor(red = 0.106, green = 0.369, blue = 0.482, alpha = 1.0)           // #1B5E7B
    val OnPrimary = UIColor.whiteColor
    val PrimaryContainer = UIColor(red = 0.816, green = 0.910, blue = 0.949, alpha = 1.0)  // #D0E8F2
    val OnPrimaryContainer = UIColor(red = 0.0, green = 0.122, blue = 0.165, alpha = 1.0)  // #001F2A

    val Secondary = UIColor(red = 0.290, green = 0.396, blue = 0.447, alpha = 1.0)         // #4A6572
    val SecondaryContainer = UIColor(red = 0.804, green = 0.898, blue = 0.941, alpha = 1.0) // #CDE5F0

    val Background = UIColor(red = 0.961, green = 0.969, blue = 0.980, alpha = 1.0)        // #F5F7FA
    val Surface = UIColor.whiteColor
    val OnSurface = UIColor(red = 0.102, green = 0.110, blue = 0.118, alpha = 1.0)         // #1A1C1E
    val SurfaceVariant = UIColor(red = 0.906, green = 0.922, blue = 0.941, alpha = 1.0)    // #E7EBF0
    val OnSurfaceVariant = UIColor(red = 0.259, green = 0.278, blue = 0.306, alpha = 1.0)  // #42474E

    val Error = UIColor(red = 0.729, green = 0.102, blue = 0.102, alpha = 1.0)             // #BA1A1A
    val PriceColor = UIColor(red = 0.180, green = 0.490, blue = 0.196, alpha = 1.0)        // #2E7D32
    val SuccessColor = UIColor(red = 0.180, green = 0.490, blue = 0.196, alpha = 1.0)      // #2E7D32
    val SuccessContainer = UIColor(red = 0.725, green = 0.965, blue = 0.792, alpha = 1.0)  // #B9F6CA

    val SurfaceVariant40 = UIColor(red = 0.906, green = 0.922, blue = 0.941, alpha = 0.4)
    val SurfaceVariant50 = UIColor(red = 0.906, green = 0.922, blue = 0.941, alpha = 0.5)
    val PriceBackground = UIColor(red = 0.180, green = 0.490, blue = 0.196, alpha = 0.1)   // #2E7D321A

    val WhiteOverlay10 = UIColor(red = 1.0, green = 1.0, blue = 1.0, alpha = 0.10)
    val WhiteOverlay15 = UIColor(red = 1.0, green = 1.0, blue = 1.0, alpha = 0.15)
    val WhiteOverlay20 = UIColor(red = 1.0, green = 1.0, blue = 1.0, alpha = 0.20)
    val WhiteOverlay50 = UIColor(red = 1.0, green = 1.0, blue = 1.0, alpha = 0.50)
    val WhiteOverlay85 = UIColor(red = 1.0, green = 1.0, blue = 1.0, alpha = 0.85)
}
