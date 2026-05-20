// Color palette matching native.web ShoppingTheme
export const Colors = {
  Primary: '#1B5E7B',
  OnPrimary: '#FFFFFF',
  PrimaryContainer: '#D0E8F2',
  OnPrimaryContainer: '#001F2A',

  Secondary: '#4A6572',
  OnSecondary: '#FFFFFF',
  SecondaryContainer: '#CDE5F0',

  Background: '#F5F7FA',
  Surface: '#FFFFFF',
  OnSurface: '#1A1C1E',
  SurfaceVariant: '#E7EBF0',
  OnSurfaceVariant: '#42474E',

  Error: '#BA1A1A',
  ErrorContainer: '#FFDAD6',
  OnErrorContainer: '#410002',

  Outline: '#72787E',
  OutlineVariant: '#C2C7CE',

  PriceColor: '#2E7D32',
  SuccessColor: '#2E7D32',
  SuccessContainer: '#B9F6CA',

  // Derived colors with alpha
  SurfaceVariant40: '#E7EBF066', // 40% opacity
  SurfaceVariant50: '#E7EBF080', // 50% opacity
  PriceBackground: '#2E7D321A', // 10% opacity

  // Semi-transparent white overlays (for dark backgrounds)
  WhiteOverlay10: 'rgba(255,255,255,0.10)',
  WhiteOverlay15: 'rgba(255,255,255,0.15)',
  WhiteOverlay20: 'rgba(255,255,255,0.2)',
  WhiteOverlay50: 'rgba(255,255,255,0.5)',
  WhiteOverlay85: 'rgba(255,255,255,0.85)',
} as const

export const COMPACT_BREAKPOINT = 480
