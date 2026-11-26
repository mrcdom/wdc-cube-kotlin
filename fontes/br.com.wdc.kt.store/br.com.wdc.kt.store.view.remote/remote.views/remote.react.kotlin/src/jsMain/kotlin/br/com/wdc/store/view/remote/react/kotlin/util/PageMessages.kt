package br.com.wdc.store.view.remote.react.kotlin.util

import br.com.wdc.framework.cube.remote.bridge.RemoteScope
import br.com.wdc.store.i18n.Messages
import kotlinx.browser.window

/** The page's UI texts, in the browser's language (`navigator.language`; en-US when it is not one of the catalog's). */
val messages: Messages by lazy { Messages.of(window.navigator.language) }

/**
 * A message a presenter put in the scope field [name], in the page's language. The host sends it as
 * `{"key": "...", "args": {...}}`; `null` when the field is absent or has no key.
 */
fun RemoteScope.message(name: String): String? {
    val message = obj(name) ?: return null
    val key = message.string("key") ?: return null
    val args = message.obj("args")
    val names = if (args == null) emptyArray() else js("Object").keys(message["args"]).unsafeCast<Array<String>>()
    return messages.text(key, names.associateWith { args?.string(it) })
}
