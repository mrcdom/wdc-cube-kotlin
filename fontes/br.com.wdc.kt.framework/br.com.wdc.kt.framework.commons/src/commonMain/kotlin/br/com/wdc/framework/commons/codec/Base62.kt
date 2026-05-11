package br.com.wdc.framework.commons.codec

import kotlin.math.ceil
import kotlin.math.ln

object Base62 {

    private const val STANDARD_BASE = 256
    private const val TARGET_BASE = 62

    private val ALPHABET = byteArrayOf(
        '0'.code.toByte(), '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(),
        '4'.code.toByte(), '5'.code.toByte(), '6'.code.toByte(), '7'.code.toByte(),
        '8'.code.toByte(), '9'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte(),
        'C'.code.toByte(), 'D'.code.toByte(), 'E'.code.toByte(), 'F'.code.toByte(),
        'G'.code.toByte(), 'H'.code.toByte(), 'I'.code.toByte(), 'J'.code.toByte(),
        'K'.code.toByte(), 'L'.code.toByte(), 'M'.code.toByte(), 'N'.code.toByte(),
        'O'.code.toByte(), 'P'.code.toByte(), 'Q'.code.toByte(), 'R'.code.toByte(),
        'S'.code.toByte(), 'T'.code.toByte(), 'U'.code.toByte(), 'V'.code.toByte(),
        'W'.code.toByte(), 'X'.code.toByte(), 'Y'.code.toByte(), 'Z'.code.toByte(),
        'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte(),
        'e'.code.toByte(), 'f'.code.toByte(), 'g'.code.toByte(), 'h'.code.toByte(),
        'i'.code.toByte(), 'j'.code.toByte(), 'k'.code.toByte(), 'l'.code.toByte(),
        'm'.code.toByte(), 'n'.code.toByte(), 'o'.code.toByte(), 'p'.code.toByte(),
        'q'.code.toByte(), 'r'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
        'u'.code.toByte(), 'v'.code.toByte(), 'w'.code.toByte(), 'x'.code.toByte(),
        'y'.code.toByte(), 'z'.code.toByte()
    )

    private val LOOKUP: ByteArray = ByteArray(256).also { lookup ->
        for (i in ALPHABET.indices) {
            lookup[ALPHABET[i].toInt() and 0xFF] = (i and 0xFF).toByte()
        }
    }

    fun encode(message: ByteArray): ByteArray {
        val indices = convert(message, STANDARD_BASE, TARGET_BASE)
        return translate(indices, ALPHABET)
    }

    fun encodeToString(message: ByteArray): String = String(encode(message))

    fun decode(encoded: ByteArray): ByteArray {
        require(isBase62Encoding(encoded)) { "Input is not encoded correctly" }
        val prepared = translate(encoded, LOOKUP)
        return convert(prepared, TARGET_BASE, STANDARD_BASE)
    }

    fun decodeFromString(encoded: String?): ByteArray? {
        return encoded?.let { decode(it.toByteArray()) }
    }

    fun isBase62Encoding(bytes: ByteArray?): Boolean {
        if (bytes == null) return false
        return bytes.all { b ->
            b in '0'.code.toByte()..'9'.code.toByte() ||
                    b in 'a'.code.toByte()..'z'.code.toByte() ||
                    b in 'A'.code.toByte()..'Z'.code.toByte()
        }
    }

    private fun translate(indices: ByteArray, dictionary: ByteArray): ByteArray {
        return ByteArray(indices.size) { i -> dictionary[indices[i].toInt() and 0xFF] }
    }

    private fun convert(message: ByteArray, sourceBase: Int, targetBase: Int): ByteArray {
        val estimatedLength = estimateOutputLength(message.size, sourceBase, targetBase)
        val out = ArrayList<Byte>(estimatedLength)
        var source = message

        while (source.isNotEmpty()) {
            val quotient = ArrayList<Byte>(source.size)
            var remainder = 0

            for (i in source.indices) {
                val accumulator = (source[i].toInt() and 0xFF) + remainder * sourceBase
                val digit = (accumulator - (accumulator % targetBase)) / targetBase
                remainder = accumulator % targetBase
                if (quotient.size > 0 || digit > 0) {
                    quotient.add(digit.toByte())
                }
            }

            out.add(remainder.toByte())
            source = quotient.toByteArray()
        }

        // pad output with zeroes corresponding to the number of leading zeroes in the message
        for (i in 0 until message.size - 1) {
            if (message[i] != 0.toByte()) break
            out.add(0)
        }

        return out.toByteArray().reversedArray()
    }

    private fun estimateOutputLength(inputLength: Int, sourceBase: Int, targetBase: Int): Int {
        return ceil(ln(sourceBase.toDouble()) / ln(targetBase.toDouble()) * inputLength).toInt()
    }
}
