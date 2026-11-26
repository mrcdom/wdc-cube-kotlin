package br.com.wdc.store.i18n

/**
 * The UI texts in one [language]: each key of the catalog (`messages.<language>.json`), with its `{placeholders}`
 * filled. A key missing in the language falls back to the default language's text, and then to the key itself.
 * The typed accessors (`messages.cartTotal(total)`, one per key) are generated next to this class.
 */
class Messages(val language: Language) {

    companion object {
        /** The texts for a language tag as a platform gives it (`pt-BR`, `pt_BR`, `en`…): see [Language.of]. */
        fun of(tag: String?): Messages = Messages(Language.of(tag))

        private val PLACEHOLDER = Regex("\\{([A-Za-z0-9_]+)\\}")
    }

    private val catalog = catalogOf(language)

    /** The text of [key], with each `{name}` replaced by the argument of that name (left as it is when not given). */
    fun text(key: String, args: Map<String, Any?> = emptyMap()): String {
        val pattern = catalog[key] ?: catalogOf(Language.Default)[key] ?: return key
        if (args.isEmpty()) return pattern
        return PLACEHOLDER.replace(pattern) { match ->
            val name = match.groupValues[1]
            if (args.containsKey(name)) args[name].toString() else match.value
        }
    }

    fun text(key: String, vararg args: Pair<String, Any?>): String = text(key, args.toMap())

    /** A civil date in this language's format (the catalog's `format.date`: `{month}/{day}/{year}` in en-US). */
    fun date(year: Int, month: Int, day: Int): String =
        text("format.date", "year" to year, "month" to month.toString().padStart(2, '0'), "day" to day.toString().padStart(2, '0'))
}
