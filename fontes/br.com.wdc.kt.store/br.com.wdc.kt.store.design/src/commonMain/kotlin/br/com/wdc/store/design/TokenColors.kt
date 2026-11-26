package br.com.wdc.store.design

/** Reads a color token (`0xAARRGGBB`, [StoreTokens.Color]) the way each view technology needs it. */
object TokenColors {

    fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()

    fun red(argb: Long): Int = ((argb shr 16) and 0xFF).toInt()

    fun green(argb: Long): Int = ((argb shr 8) and 0xFF).toInt()

    fun blue(argb: Long): Int = (argb and 0xFF).toInt()

    /** As a 32-bit ARGB `Int`, as Android's `Color` takes it. */
    fun argbInt(argb: Long): Int = argb.toInt()

    /** As CSS: `#RRGGBB` when opaque, `rgba(r, g, b, a)` otherwise (alpha to two decimals). */
    fun css(argb: Long): String {
        val a = alpha(argb)
        if (a == 255) return "#" + hex(red(argb)) + hex(green(argb)) + hex(blue(argb))
        val alpha = ((a * 100.0 / 255.0) + 0.5).toInt() / 100.0
        return "rgba(${red(argb)}, ${green(argb)}, ${blue(argb)}, ${alpha.toString().trimEnd('0').trimEnd('.')})"
    }

    /** A font family list as CSS: names quoted, the generic family (`sans-serif`) not. */
    fun cssFamily(names: List<String>): String =
        names.joinToString(", ") { if (it.contains('-') && it.endsWith("serif")) it else "'$it'" }

    private fun hex(channel: Int): String = channel.toString(16).uppercase().padStart(2, '0')
}
