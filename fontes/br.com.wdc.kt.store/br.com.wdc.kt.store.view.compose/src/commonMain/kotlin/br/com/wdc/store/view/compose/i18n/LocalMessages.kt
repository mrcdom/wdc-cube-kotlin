package br.com.wdc.store.view.compose.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale
import br.com.wdc.store.i18n.Language
import br.com.wdc.store.i18n.Messages
import br.com.wdc.store.presentation.i18n.Message

/**
 * The UI texts of the Compose views. [br.com.wdc.store.view.compose.theme.StoreTheme] provides the ones of the
 * platform's language ([platformMessages]); outside it, US English.
 */
val LocalMessages = staticCompositionLocalOf { Messages(Language.Default) }

/** The texts in the platform's language (`Locale.current`); a language without a catalog falls back to US English. */
fun platformMessages(): Messages = Messages.of(Locale.current.toLanguageTag())

/** The text of a presenter's [message] in this language. */
fun Messages.text(message: Message): String = text(message.key, message.args)
