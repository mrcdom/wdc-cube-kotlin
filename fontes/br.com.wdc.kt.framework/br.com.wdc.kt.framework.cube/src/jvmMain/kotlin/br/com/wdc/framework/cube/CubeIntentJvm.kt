package br.com.wdc.framework.cube

import br.com.wdc.framework.cube.util.QueryStringParser
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
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

fun CubeIntent.Companion.parse(placeStr: String?): CubeIntent {
    val intent = CubeIntent()
    if (!placeStr.isNullOrBlank()) {
        val parts = placeStr.split("?", limit = 2)
        intent.place = GenericPlace(-1, parts[0])
        if (parts.size > 1) {
            QueryStringParser.parse(intent, parts[1], StandardCharsets.UTF_8)
        }
    } else {
        intent.place = GenericPlace(-1, "unknown")
    }
    return intent
}

private class GenericPlace(
    override val id: Int,
    override val placeName: String,
) : CubePlace {
    override fun <A : CubeApplication> presenterFactory(): (A) -> CubePresenter =
        throw AssertionError("Must not be invoked")
}
