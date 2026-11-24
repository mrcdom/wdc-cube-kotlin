package br.com.wdc.framework.rest.policy

/**
 * How a textual field is shown to a role that may see it only partially — a document number with only the last
 * digits, a login with only the first letter.
 *
 * A masked field can be read, but not used in a filter or an ordering (that would reveal what the mask hides), and not
 * written: a [DataGrant] that masks a field cannot also write it, so a masked value sent back is refused rather than
 * stored over the real one.
 */
fun interface Mask {

    fun apply(value: String): String

    companion object {
        /** The character that replaces what a mask hides. */
        const val FILL: Char = '*'

        /** Keeps the first [n] characters and hides the rest (`"jdoe"` → `"j***"`). */
        fun keepFirst(n: Int): Mask = Mask { value ->
            val kept = minOf(n, value.length)
            value.take(kept) + FILL.toString().repeat(value.length - kept)
        }

        /** Keeps the last [n] characters and hides the rest (`"12345678909"` → `"*********09"`). */
        fun keepLast(n: Int): Mask = Mask { value ->
            val kept = minOf(n, value.length)
            FILL.toString().repeat(value.length - kept) + value.takeLast(kept)
        }
    }
}
