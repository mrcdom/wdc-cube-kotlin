package br.com.wdc.framework.domain.criteria

/**
 * Criterion on a text field.
 *
 * Extends [ComparableCriterion] because text can be ordered too — `name.ge("M")` is a legitimate query — and
 * adds pattern searches.
 *
 * **Wildcards are part of the value.** `like("%CAFE%")` differs from `like("CAFE")`, exactly as in SQL.
 * Adding them automatically would take away from whoever writes the query the ability to anchor at the start —
 * which is precisely what decides whether an index will be used.
 *
 * **In [like] and [ilike] the value is a pattern; in [containing] and [startingWith], it is text.** The latter
 * two escape `%`, `_` and the backslash itself in the fragment before adding the wildcards: what the person
 * typed is searched literally, and `50%` doesn't become "starts with 50". To build a pattern by hand with
 * outside text, [escape].
 *
 * @param C type of the `XxxCriteria` that contains this field
 */
class TextCriterion<C>(owner: C?, name: String) : ComparableCriterion<C, String>(owner, name) {

    // Covariant return: without this, or() would return the superclass and chaining would lose like and ilike.
    override fun or(): TextCriterion<C> {
        super.or()
        return this
    }

    override fun and(): TextCriterion<C> {
        super.and()
        return this
    }

    /** `LIKE`, case-sensitive. Null or empty text adds no request. */
    fun like(pattern: String?): C = if (pattern.isNullOrEmpty()) owner() else add(Operator.LIKE, pattern)

    /** `ILIKE`, case-insensitive. Null or empty text adds no request. */
    fun ilike(pattern: String?): C = if (pattern.isNullOrEmpty()) owner() else add(Operator.ILIKE, pattern)

    /** `ILIKE '%value%'`, with the literal value — the most common fragment search. */
    fun containing(fragment: String?): C = if (fragment.isNullOrEmpty()) owner() else ilike("%${escape(fragment)}%")

    /** `ILIKE 'value%'`, with the literal value — anchored at the start, which lets the database use an index. */
    fun startingWith(prefix: String?): C = if (prefix.isNullOrEmpty()) owner() else ilike("${escape(prefix)}%")

    companion object {
        /** The escape character of patterns; the SQL translator declares it (`ESCAPE '\'`). */
        const val ESCAPE: Char = '\\'

        /** The text as a literal in a pattern: `%`, `_` and [ESCAPE] itself get the escape in front. */
        fun escape(text: String): String = buildString(text.length) {
            for (ch in text) {
                if (ch == '%' || ch == '_' || ch == ESCAPE) append(ESCAPE)
                append(ch)
            }
        }
    }
}
