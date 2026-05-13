package br.com.wdc.framework.commons.serialization

import br.com.wdc.framework.commons.lang.CoerceUtils
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

object InputCoerceUtils {

    // :: String

    fun asString(input: ExtensibleObjectInput, defaultValue: String? = null): String? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.STRING -> input.nextString()
        SerializationToken.BOOLEAN -> CoerceUtils.asString(input.nextBoolean(), defaultValue)
        SerializationToken.NUMBER -> CoerceUtils.asString(input.nextNumber(), defaultValue)
        else -> throw invalidToken(input)
    }

    fun asTrimmedString(input: ExtensibleObjectInput, defaultValue: String? = null): String? =
        asString(input, defaultValue)?.trim()

    fun asLowerCaseString(input: ExtensibleObjectInput, defaultValue: String? = null): String? =
        asString(input, defaultValue)?.lowercase()

    fun asUpperCaseString(input: ExtensibleObjectInput, defaultValue: String? = null): String? =
        asString(input, defaultValue)?.uppercase()

    // :: Boolean

    fun asBoolean(input: ExtensibleObjectInput, defaultValue: Boolean? = null): Boolean? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.BOOLEAN -> input.nextBoolean()
        SerializationToken.STRING -> CoerceUtils.asBoolean(input.nextString())
        SerializationToken.NUMBER -> CoerceUtils.asBoolean(input.nextNumber(), defaultValue)
        else -> throw invalidToken(input)
    }

    // :: Byte

    fun asByte(input: ExtensibleObjectInput, defaultValue: Byte? = null): Byte? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> input.nextInt().toByte()
        SerializationToken.BOOLEAN -> CoerceUtils.asByte(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asByte(input.nextString())
        else -> throw invalidToken(input)
    }

    // :: Short

    fun asShort(input: ExtensibleObjectInput, defaultValue: Short? = null): Short? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> input.nextInt().toShort()
        SerializationToken.BOOLEAN -> CoerceUtils.asShort(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asShort(input.nextString())
        else -> throw invalidToken(input)
    }

    // :: Integer

    fun asInteger(input: ExtensibleObjectInput, defaultValue: Int? = null): Int? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> input.nextInt()
        SerializationToken.BOOLEAN -> CoerceUtils.asInteger(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asInteger(input.nextString())
        else -> throw invalidToken(input)
    }

    // :: Character

    fun asCharacter(input: ExtensibleObjectInput, defaultValue: Char? = null): Char? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.STRING -> CoerceUtils.asCharacter(input.nextString())
        SerializationToken.NUMBER -> CoerceUtils.asCharacter(input.nextInt())
        SerializationToken.BOOLEAN -> CoerceUtils.asCharacter(input.nextBoolean())
        else -> throw invalidToken(input)
    }

    // :: Long

    fun asLong(input: ExtensibleObjectInput, defaultValue: Long? = null): Long? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> input.nextLong()
        SerializationToken.BOOLEAN -> CoerceUtils.asLong(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asLong(input.nextString())
        else -> throw invalidToken(input)
    }

    // :: BigInteger

    fun asBigInteger(input: ExtensibleObjectInput, defaultValue: BigInteger? = null): BigInteger? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asBigInteger(input.nextLong())
        SerializationToken.BOOLEAN -> CoerceUtils.asBigInteger(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asBigInteger(input.nextString())
        else -> throw invalidToken(input)
    }

    // :: Float

    fun asFloat(input: ExtensibleObjectInput, defaultValue: Float? = null): Float? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> input.nextDouble().toFloat()
        SerializationToken.BOOLEAN -> CoerceUtils.asFloat(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asFloat(input.nextString())
        else -> throw invalidToken(input)
    }

    // :: Double

    fun asDouble(input: ExtensibleObjectInput, defaultValue: Double? = null): Double? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> input.nextDouble()
        SerializationToken.BOOLEAN -> CoerceUtils.asDouble(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asDouble(input.nextString())
        else -> throw invalidToken(input)
    }

    // :: BigDecimal

    fun asBigDecimal(input: ExtensibleObjectInput, defaultValue: BigDecimal? = null): BigDecimal? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asBigDecimal(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asBigDecimal(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asBigDecimal(input.nextString())
        else -> throw invalidToken(input)
    }

    // :: ByteArray

    fun asByteArray(input: ExtensibleObjectInput, defaultValue: ByteArray? = null): ByteArray? = when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.STRING -> CoerceUtils.asByteArray(input.nextString())
        SerializationToken.BOOLEAN -> CoerceUtils.asByteArray(input.nextBoolean(), defaultValue)
        SerializationToken.NUMBER -> CoerceUtils.asByteArray(input.nextNumber(), defaultValue)
        else -> throw invalidToken(input)
    }

    // :: Internal

    private fun invalidToken(input: ExtensibleObjectInput): IllegalStateException =
        IllegalStateException("No valid value found. peek() = ${input.peek()}")
}
