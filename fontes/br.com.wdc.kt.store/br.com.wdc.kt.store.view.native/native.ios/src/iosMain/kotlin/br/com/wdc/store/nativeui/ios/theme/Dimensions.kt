package br.com.wdc.store.nativeui.ios.theme

/**
 * A dimension token ([br.com.wdc.store.design.StoreTokens] spacing, radius or font size, in points) as UIKit takes it:
 * `Spacing.Lg.pt`, `Radius.Sm.pt`, `FontSize.Body.pt`.
 */
val Int.pt: Double get() = toDouble()
