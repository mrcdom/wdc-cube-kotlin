package br.com.wdc.framework.commons.serialization

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.lang.asByteArrayFromHex
import br.com.wdc.framework.commons.lang.asJavaDate
import br.com.wdc.framework.commons.lang.asJavaBigDecimal
import br.com.wdc.framework.commons.lang.asJavaBigInteger
import br.com.wdc.framework.commons.lang.asJavaByteArray
import br.com.wdc.framework.commons.lang.asJavaLocalDate
import br.com.wdc.framework.commons.lang.asJavaLocalDateTime
import br.com.wdc.framework.commons.lang.asJavaOffsetDateTime
import br.com.wdc.framework.commons.lang.asJavaSqlDate
import br.com.wdc.framework.commons.lang.asJavaTimestamp
import br.com.wdc.framework.commons.lang.asJavaZonedDateTime
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

// :: BigInteger (java.math)

fun InputCoerceUtils.asJavaBigInteger(input: ExtensibleObjectInput, defaultValue: BigInteger? = null): BigInteger? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaBigInteger(input.nextLong())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaBigInteger(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaBigInteger(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: BigDecimal (java.math)

fun InputCoerceUtils.asJavaBigDecimal(input: ExtensibleObjectInput, defaultValue: BigDecimal? = null): BigDecimal? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaBigDecimal(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaBigDecimal(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaBigDecimal(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: java.util.Date

fun InputCoerceUtils.asJavaDate(input: ExtensibleObjectInput, defaultValue: java.util.Date? = null): java.util.Date? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaDate(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaDate(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaDate(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: java.sql.Timestamp

fun InputCoerceUtils.asJavaTimestamp(input: ExtensibleObjectInput, defaultValue: java.sql.Timestamp? = null): java.sql.Timestamp? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaTimestamp(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaTimestamp(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaTimestamp(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: java.sql.Date

fun InputCoerceUtils.asJavaSqlDate(input: ExtensibleObjectInput, defaultValue: java.sql.Date? = null): java.sql.Date? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaSqlDate(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaSqlDate(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaSqlDate(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: java.time.LocalDate

fun InputCoerceUtils.asJavaLocalDate(input: ExtensibleObjectInput, defaultValue: LocalDate? = null): LocalDate? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaLocalDate(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaLocalDate(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaLocalDate(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: java.time.LocalDateTime

fun InputCoerceUtils.asJavaLocalDateTime(input: ExtensibleObjectInput, defaultValue: LocalDateTime? = null): LocalDateTime? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaLocalDateTime(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaLocalDateTime(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaLocalDateTime(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: OffsetDateTime

fun InputCoerceUtils.asJavaOffsetDateTime(input: ExtensibleObjectInput, defaultValue: OffsetDateTime? = null): OffsetDateTime? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaOffsetDateTime(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaOffsetDateTime(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaOffsetDateTime(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: ZonedDateTime

fun InputCoerceUtils.asJavaZonedDateTime(input: ExtensibleObjectInput, defaultValue: ZonedDateTime? = null): ZonedDateTime? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.NUMBER -> CoerceUtils.asJavaZonedDateTime(input.nextDouble())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaZonedDateTime(input.nextBoolean())
        SerializationToken.STRING -> CoerceUtils.asJavaZonedDateTime(input.nextString())
        else -> throw invalidInputToken(input)
    }

// :: ByteArray (Base64 - java.util)

fun InputCoerceUtils.asJavaByteArray(input: ExtensibleObjectInput, defaultValue: ByteArray? = null): ByteArray? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.STRING -> CoerceUtils.asJavaByteArray(input.nextString())
        SerializationToken.BOOLEAN -> CoerceUtils.asJavaByteArray(input.nextBoolean(), defaultValue)
        SerializationToken.NUMBER -> CoerceUtils.asJavaByteArray(input.nextNumber(), defaultValue)
        else -> throw invalidInputToken(input)
    }

// :: ByteArray from Hex

fun InputCoerceUtils.asByteArrayFromHex(input: ExtensibleObjectInput, defaultValue: ByteArray? = null): ByteArray? =
    when (input.peek()) {
        SerializationToken.NULL -> { input.nextNull<Any?>(); defaultValue }
        SerializationToken.STRING -> CoerceUtils.asByteArrayFromHex(input.nextString())
        SerializationToken.BOOLEAN -> CoerceUtils.asByteArrayFromHex(input.nextBoolean(), defaultValue)
        SerializationToken.NUMBER -> CoerceUtils.asByteArrayFromHex(input.nextNumber(), defaultValue)
        else -> throw invalidInputToken(input)
    }

// :: Internal

private fun invalidInputToken(input: ExtensibleObjectInput): IllegalStateException =
    IllegalStateException("No valid value found. peek() = ${input.peek()}")
