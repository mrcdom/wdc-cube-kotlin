@file:Suppress("UNCHECKED_CAST")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.wdc.framework.commons.serialization

import kotlinx.cinterop.*
import platform.Foundation.*

/**
 * iOS JSON factory implementations using NSJSONSerialization.
 */
fun JsonInputFactory.installIos() {
    install { json ->
        val data = (json as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw IllegalStateException("Failed to encode JSON string")
        val jsonObj = NSJSONSerialization.JSONObjectWithData(data, 0u, null)
            ?: throw IllegalStateException("Failed to parse JSON")
        val kotlinValue = nsToKotlin(jsonObj)
        val input = when (kotlinValue) {
            is Map<*, *> -> MapOrListInput(kotlinValue as Map<String, Any?>)
            is List<*> -> MapOrListInput(kotlinValue)
            else -> throw IllegalStateException("JSON root must be an object or array, got: $kotlinValue")
        }
        JsonStringInput(input)
    }
}

fun JsonOutputFactory.installIos() {
    install {
        val output = MapOrListOutput()
        JsonStringOutput(output) {
            val value = output.value
            val nsValue = kotlinToNs(value)
            val data = NSJSONSerialization.dataWithJSONObject(nsValue!!, 0u, null)
                ?: throw IllegalStateException("Failed to serialize JSON")
            NSString.create(data, NSUTF8StringEncoding) as String
        }
    }
}

private fun nsToKotlin(value: Any?): Any? {
    return when (value) {
        null -> null
        is NSNull -> null
        is NSNumber -> {
            // Check if this NSNumber represents a boolean
            // NSNumber wrapping BOOL has objCType "c" or "B"
            val className = value.objCType()?.toKString()
            if (className == "c" || className == "B") {
                value.boolValue
            } else {
                val d = value.doubleValue
                val l = d.toLong()
                if (d == l.toDouble() && !d.isInfinite()) {
                    if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
                } else {
                    d
                }
            }
        }
        is NSString -> value.toString()
        is List<*> -> {
            val list = ArrayList<Any?>(value.size)
            for (item in value) {
                list.add(nsToKotlin(item))
            }
            list
        }
        is Map<*, *> -> {
            val map = LinkedHashMap<String, Any?>()
            for ((k, v) in value) {
                map[k.toString()] = nsToKotlin(v)
            }
            map
        }
        else -> value.toString()
    }
}

private fun kotlinToNs(value: Any?): Any? {
    return when (value) {
        null -> NSNull()
        is String -> value as NSString
        is Int -> NSNumber(int = value)
        is Long -> NSNumber(longLong = value)
        is Double -> NSNumber(double = value)
        is Float -> NSNumber(float = value)
        is Boolean -> NSNumber(bool = value)
        is Number -> NSNumber(double = value.toDouble())
        is Map<*, *> -> {
            val dict = NSMutableDictionary()
            for ((k, v) in value) {
                val nsVal = kotlinToNs(v)
                if (nsVal != null) {
                    dict.setValue(nsVal, forKey = k.toString())
                }
            }
            dict
        }
        is List<*> -> {
            val arr = NSMutableArray()
            for (item in value) {
                val nsItem = kotlinToNs(item)
                if (nsItem != null) {
                    arr.addObject(nsItem as platform.darwin.NSObject)
                }
            }
            arr
        }
        is ByteArray -> {
            val arr = NSMutableArray()
            for (b in value) {
                arr.addObject(NSNumber(int = (b.toInt() and 0xFF)))
            }
            arr
        }
        else -> value.toString() as NSString
    }
}
