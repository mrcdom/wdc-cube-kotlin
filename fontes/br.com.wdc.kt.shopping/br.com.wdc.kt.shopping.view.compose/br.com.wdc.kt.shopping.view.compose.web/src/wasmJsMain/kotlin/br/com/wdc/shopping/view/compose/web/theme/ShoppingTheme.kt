package br.com.wdc.shopping.view.compose.web.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta principal - tons de azul-petróleo elegante
val Primary = Color(0xFF1B5E7B)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFD0E8F2)
val OnPrimaryContainer = Color(0xFF001F2A)

val Secondary = Color(0xFF4A6572)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFCDE5F0)
val OnSecondaryContainer = Color(0xFF051F2C)

val Tertiary = Color(0xFF5C6BC0)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFDDE0FF)
val OnTertiaryContainer = Color(0xFF151A60)

val Background = Color(0xFFF5F7FA)
val OnBackground = Color(0xFF1A1C1E)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF1A1C1E)
val SurfaceVariant = Color(0xFFE7EBF0)
val OnSurfaceVariant = Color(0xFF42474E)

val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

val Outline = Color(0xFF72787E)
val OutlineVariant = Color(0xFFC2C7CE)

val PriceColor = Color(0xFF2E7D32)
val SuccessColor = Color(0xFF2E7D32)
val SuccessContainer = Color(0xFFB9F6CA)

private val ShoppingColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

@Composable
fun ShoppingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShoppingColorScheme,
        typography = Typography(),
        content = content
    )
}
