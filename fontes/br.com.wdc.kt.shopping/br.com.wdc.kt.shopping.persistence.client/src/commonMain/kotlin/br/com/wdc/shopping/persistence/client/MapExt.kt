@file:Suppress("UNCHECKED_CAST")

package br.com.wdc.shopping.persistence.client

internal fun Map<String, Any?>.string(key: String): String =
    this[key] as String

internal fun Map<String, Any?>.stringOrNull(key: String): String? =
    this[key] as? String

internal fun Map<String, Any?>.long(key: String): Long =
    (this[key] as Number).toLong()

internal fun Map<String, Any?>.longOrNull(key: String): Long? =
    (this[key] as? Number)?.toLong()

internal fun Map<String, Any?>.int(key: String): Int =
    (this[key] as Number).toInt()

internal fun Map<String, Any?>.double(key: String): Double =
    (this[key] as Number).toDouble()

internal fun Map<String, Any?>.doubleOrNull(key: String): Double? =
    (this[key] as? Number)?.toDouble()

internal fun Map<String, Any?>.boolean(key: String): Boolean =
    this[key] as Boolean

internal fun Map<String, Any?>.map(key: String): Map<String, Any?> =
    this[key] as Map<String, Any?>

internal fun Map<String, Any?>.mapOrNull(key: String): Map<String, Any?>? =
    this[key] as? Map<String, Any?>

internal fun Map<String, Any?>.list(key: String): List<Map<String, Any?>> =
    this[key] as List<Map<String, Any?>>

internal fun Map<String, Any?>.listOrNull(key: String): List<Map<String, Any?>>? =
    this[key] as? List<Map<String, Any?>>

internal fun bytesToHex(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        sb.append(HEX_CHARS[v ushr 4])
        sb.append(HEX_CHARS[v and 0x0F])
    }
    return sb.toString()
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()
