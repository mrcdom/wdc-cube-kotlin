import { StoreTokens } from './tokens'

const color = StoreTokens.color

// Color palette from the WDC Store's shared design tokens (tokens.ts)
export const Colors = {
  Primary: color.primary,
  OnPrimary: color.onPrimary,
  PrimaryContainer: color.primaryContainer,
  OnPrimaryContainer: color.onPrimaryContainer,

  Secondary: color.secondary,
  OnSecondary: color.onSecondary,
  SecondaryContainer: color.secondaryContainer,

  Background: color.background,
  Surface: color.surface,
  OnSurface: color.onSurface,
  SurfaceVariant: color.surfaceVariant,
  OnSurfaceVariant: color.onSurfaceVariant,

  Error: color.error,
  ErrorContainer: color.errorContainer,
  OnErrorContainer: color.onErrorContainer,

  Outline: color.outline,
  OutlineVariant: color.outlineVariant,

  PriceColor: color.price,
  SuccessColor: color.success,
  SuccessContainer: color.successContainer,

  // Derived colors with alpha
  SurfaceVariant40: color.surfaceVariant40,
  SurfaceVariant50: color.surfaceVariant50,
  PriceBackground: color.priceBackground,

  // Semi-transparent white overlays (for dark backgrounds)
  WhiteOverlay10: color.whiteOverlay10,
  WhiteOverlay15: color.whiteOverlay15,
  WhiteOverlay20: color.whiteOverlay20,
  WhiteOverlay50: color.whiteOverlay50,
  WhiteOverlay85: color.whiteOverlay85,
} as const

export const FontFamily = StoreTokens.fontFamily.base

/**
 * A token dimension as a CSS length. In MUI's `sx`, a bare number means theme units for spacing (`p: 2` = 16 px)
 * and a multiple of `shape.borderRadius` for `borderRadius`, so token values go through here.
 */
export const toPx = (value: number): string => `${value}px`

export const COMPACT_BREAKPOINT = 480
