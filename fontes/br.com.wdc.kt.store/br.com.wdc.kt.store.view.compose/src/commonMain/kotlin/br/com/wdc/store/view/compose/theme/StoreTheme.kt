package br.com.wdc.store.view.compose.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.view.compose.i18n.LocalMessages
import br.com.wdc.store.view.compose.i18n.platformMessages

// Main palette - elegant petrol-blue tones, from the design tokens (StoreTokens.Color)
val Primary = Color(StoreTokens.Color.Primary)
val OnPrimary = Color(StoreTokens.Color.OnPrimary)
val PrimaryContainer = Color(StoreTokens.Color.PrimaryContainer)
val OnPrimaryContainer = Color(StoreTokens.Color.OnPrimaryContainer)

val Secondary = Color(StoreTokens.Color.Secondary)
val OnSecondary = Color(StoreTokens.Color.OnSecondary)
val SecondaryContainer = Color(StoreTokens.Color.SecondaryContainer)
val OnSecondaryContainer = Color(StoreTokens.Color.OnSecondaryContainer)

val Background = Color(StoreTokens.Color.Background)
val OnBackground = Color(StoreTokens.Color.OnSurface)
val Surface = Color(StoreTokens.Color.Surface)
val OnSurface = Color(StoreTokens.Color.OnSurface)
val SurfaceVariant = Color(StoreTokens.Color.SurfaceVariant)
val OnSurfaceVariant = Color(StoreTokens.Color.OnSurfaceVariant)

val Error = Color(StoreTokens.Color.Error)
val OnError = Color(StoreTokens.Color.OnError)
val ErrorContainer = Color(StoreTokens.Color.ErrorContainer)
val OnErrorContainer = Color(StoreTokens.Color.OnErrorContainer)

val Outline = Color(StoreTokens.Color.Outline)
val OutlineVariant = Color(StoreTokens.Color.OutlineVariant)

val PriceColor = Color(StoreTokens.Color.Price)
val SuccessColor = Color(StoreTokens.Color.Success)
val SuccessContainer = Color(StoreTokens.Color.SuccessContainer)

private val StoreColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
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

/** The root of every Compose app: the Store's colors and the UI texts in the platform's language ([LocalMessages]). */
@Composable
fun StoreTheme(content: @Composable () -> Unit) {
    val messages = remember { platformMessages() }
    CompositionLocalProvider(LocalMessages provides messages) {
        MaterialTheme(
            colorScheme = StoreColorScheme,
            typography = Typography(),
            content = content
        )
    }
}
