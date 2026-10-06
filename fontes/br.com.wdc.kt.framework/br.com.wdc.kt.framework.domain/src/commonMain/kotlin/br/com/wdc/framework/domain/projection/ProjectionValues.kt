package br.com.wdc.framework.domain.projection

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Valores-sentinela para montar projeções.
 *
 * Uma projeção é uma instância da própria entidade onde campo não-nulo significa "traga este campo".
 * O valor em si é irrelevante; importa ser não-nulo.
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

    /** Projeção de coleção: a forma do item mais o critério da entidade filha. */
    fun <T> singletonList(bean: T, criteria: Any?): ProjectionList<T> = ProjectionList(bean, criteria)
}
