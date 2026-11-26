package br.com.wdc.store.nativeui.web.util

import br.com.wdc.store.i18n.Messages
import br.com.wdc.store.presentation.i18n.Message
import kotlinx.browser.window

/** The page's UI texts, in the browser's language (`navigator.language`; en-US when it is not one of the catalog's). */
val messages: Messages by lazy { Messages.of(window.navigator.language) }

/** The text of a message a presenter put in a scope, in this language. */
fun Messages.show(message: Message): String = text(message.key, message.args)
