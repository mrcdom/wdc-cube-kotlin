package br.com.wdc.shopping.domain.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.OffsetDateTime

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
    val date: java.util.Date = java.util.Date(115754400000L)
    val localDate: LocalDate = LocalDate.MIN
    val offsetDateTime: OffsetDateTime = OffsetDateTime.MIN

    fun <T> singletonList(bean: T, criteria: Any?): ProjectionList<T> =
        ProjectionList(bean, criteria)
}
