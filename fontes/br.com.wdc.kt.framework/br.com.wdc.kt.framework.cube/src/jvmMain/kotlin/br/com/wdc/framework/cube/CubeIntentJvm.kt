package br.com.wdc.framework.cube

import java.math.BigDecimal
import java.math.BigInteger
import java.util.logging.Level
import java.util.logging.Logger

private val logger = Logger.getLogger(CubeIntent::class.java.name)

fun CubeIntent.getParameterAsBigDecimal(name: String, defaultValue: BigDecimal? = null): BigDecimal? {
    val value = getParameterValue(name)
    if (value is BigDecimal) return value
    if (value != null) {
        try { return BigDecimal(value.toString()) }
        catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
    }
    return defaultValue
}

fun CubeIntent.getParameterAsBigInteger(name: String, defaultValue: BigInteger? = null): BigInteger? {
    val value = getParameterValue(name)
    if (value is BigInteger) return value
    if (value != null) {
        try { return BigInteger(value.toString()) }
        catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
    }
    return defaultValue
}
