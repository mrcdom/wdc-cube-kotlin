package br.com.wdc.framework.domain.projection

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Sentinel values for building projections.
 *
 * A projection is an instance of the entity itself where a non-null field means "bring this field".
 * The value itself is irrelevant; what matters is being non-null.
 */
object ProjectionValues {
    val bool: Boolean = true
    val i8: Byte = 1
    val i16: Short = 1
    val i32: Int = 1
    val i64: Long = 1L
    val f32: Float = 1f
    val f64: Double = 1.0
    val chr: Char = 'A'
    val str: String = "~"
    val bin: ByteArray = ByteArray(0)
    val bInt: BigInteger = BigInteger.ONE
    val bDec: BigDecimal = BigDecimal.ONE
    val localDate: LocalDate = LocalDate(1970, 1, 1)
    val instant: Instant = Instant.fromEpochMilliseconds(0L)

    /**
     * Collection projection: the item shape plus the child entity's criteria and, optionally, the slice (see
     * [ProjectionList]). Order too, or the slice is arbitrary.
     */
    fun <T> singletonList(bean: T, criteria: Any?, limit: Int? = null, offset: Int? = null): ProjectionList<T> =
        ProjectionList(bean, criteria, limit, offset)
}
