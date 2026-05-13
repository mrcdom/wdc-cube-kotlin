package br.com.wdc.shopping.domain.utils

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

object ProjectionValues {

    val bool: Boolean = true
    val i8: Byte = 1
    val i16: Short = 1
    val i32: Int = 1
    val i64: Long = 1L
    val f32: Float = 1f
    val f64: Double = 1.0
    val chr: Char = 'A'
    val str: String = "dummy"
    val bin: ByteArray = ByteArray(0)

    val bInt: BigInteger = BigInteger.ONE
    val bDec: BigDecimal = BigDecimal.ONE
    val localDate: LocalDate = LocalDate(1970, 1, 1)

    val offsetDateTime: Instant = Instant.fromEpochMilliseconds(0L)

    fun <T> singletonList(bean: T, criteria: Any?): ProjectionList<T> =
        ProjectionList(bean, criteria)
}
