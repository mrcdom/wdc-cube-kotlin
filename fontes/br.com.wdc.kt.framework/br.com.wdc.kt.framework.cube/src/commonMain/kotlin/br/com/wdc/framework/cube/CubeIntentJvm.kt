package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.log.Log
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

private val logger = Log.getLogger("CubeIntent")

fun CubeIntent.getParameterAsBigDecimal(name: String, defaultValue: BigDecimal? = null): BigDecimal? {
    val value = getParameterValue(name)
    if (value is BigDecimal) return value
    if (value != null) {
        try { return BigDecimal.parseString(value.toString()) }
        catch (e: Exception) { logger.warn("NumberFormatException: {}", e.message) }
    }
    return defaultValue
}

fun CubeIntent.getParameterAsBigInteger(name: String, defaultValue: BigInteger? = null): BigInteger? {
    val value = getParameterValue(name)
    if (value is BigInteger) return value
    if (value != null) {
        try { return BigInteger.parseString(value.toString()) }
        catch (e: Exception) { logger.warn("NumberFormatException: {}", e.message) }
    }
    return defaultValue
}
