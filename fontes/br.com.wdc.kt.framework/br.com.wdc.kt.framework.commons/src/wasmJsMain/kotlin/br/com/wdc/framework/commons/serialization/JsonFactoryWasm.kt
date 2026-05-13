@file:Suppress("UNCHECKED_CAST")

package br.com.wdc.framework.commons.serialization

/**
 * wasmJs JSON factory implementations using browser's native JSON.parse/JSON.stringify
 * combined with the existing MapOrListInput/MapOrListOutput from commonMain.
 */
fun JsonInputFactory.installWasm() {
    install { json ->
        val jsValue = jsJsonParse(json)
        val kotlinValue = jsToKotlin(jsValue)
        val input = when (kotlinValue) {
            is Map<*, *> -> MapOrListInput(kotlinValue as Map<String, Any?>)
            is List<*> -> MapOrListInput(kotlinValue)
            else -> throw IllegalStateException("JSON root must be an object or array, got: $kotlinValue")
        }
        JsonStringInput(input)
    }
}

fun JsonOutputFactory.installWasm() {
    install {
        val output = MapOrListOutput()
        JsonStringOutput(output) {
            val value = output.value
            val jsValue = kotlinToJs(value)
            jsJsonStringify(jsValue)
        }
    }
}

// --- JS Interop ---

@JsFun("(s) => JSON.parse(s)")
private external fun jsParseJson(s: JsString): JsAny?

@JsFun("(o) => JSON.stringify(o)")
private external fun jsStringifyJson(o: JsAny?): JsString

@JsFun("(o) => typeof o")
private external fun jsTypeOf(o: JsAny): JsString

@JsFun("(o) => Array.isArray(o)")
private external fun jsIsArray(o: JsAny): JsBoolean

@JsFun("(o) => Object.keys(o)")
private external fun jsObjectKeys(o: JsAny): JsArray<JsString>

@JsFun("(o, k) => o[k]")
private external fun jsGetProp(o: JsAny, k: JsString): JsAny?

@JsFun("(a, i) => a[i]")
private external fun jsArrayGet(a: JsAny, i: JsNumber): JsAny?

@JsFun("(a) => a.length")
private external fun jsArrayLength(a: JsAny): JsNumber

@JsFun("() => ({})")
private external fun jsNewObject(): JsAny

@JsFun("(o, k, v) => { o[k] = v; }")
private external fun jsSetProp(o: JsAny, k: JsString, v: JsAny?)

@JsFun("() => []")
private external fun jsNewArray(): JsAny

@JsFun("(a, v) => a.push(v)")
private external fun jsArrayPush(a: JsAny, v: JsAny?)

// --- Conversion helpers ---

private fun jsJsonParse(json: String): JsAny? {
    return jsParseJson(json.toJsString())
}

private fun jsJsonStringify(value: JsAny?): String {
    return jsStringifyJson(value).toString()
}

private fun jsToKotlin(value: JsAny?): Any? {
    if (value == null) return null

    val type = jsTypeOf(value).toString()
    return when (type) {
        "string" -> (value as JsString).toString()
        "number" -> {
            val d = (value as JsNumber).toDouble()
            val l = d.toLong()
            if (d == l.toDouble() && !d.isInfinite()) {
                if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
            } else {
                d
            }
        }
        "boolean" -> (value as JsBoolean).toBoolean()
        "object" -> {
            if (jsIsArray(value).toBoolean()) {
                val len = jsArrayLength(value).toInt()
                val list = ArrayList<Any?>(len)
                for (i in 0 until len) {
                    list.add(jsToKotlin(jsArrayGet(value, i.toJsNumber())))
                }
                list
            } else {
                val keys = jsObjectKeys(value)
                val keysLen = jsArrayLength(keys).toInt()
                val map = LinkedHashMap<String, Any?>(keysLen)
                for (i in 0 until keysLen) {
                    val key = (jsArrayGet(keys, i.toJsNumber()) as JsString).toString()
                    map[key] = jsToKotlin(jsGetProp(value, key.toJsString()))
                }
                map
            }
        }
        else -> null
    }
}

private fun kotlinToJs(value: Any?): JsAny? {
    return when (value) {
        null -> null
        is String -> value.toJsString()
        is Int -> doubleToJsNumber(value.toDouble())
        is Long -> doubleToJsNumber(value.toDouble())
        is Double -> doubleToJsNumber(value)
        is Float -> doubleToJsNumber(value.toDouble())
        is Boolean -> value.toJsBoolean()
        is Number -> doubleToJsNumber(value.toDouble())
        is Map<*, *> -> {
            val obj = jsNewObject()
            for ((k, v) in value) {
                jsSetProp(obj, k.toString().toJsString(), kotlinToJs(v))
            }
            obj
        }
        is List<*> -> {
            val arr = jsNewArray()
            for (item in value) {
                jsArrayPush(arr, kotlinToJs(item))
            }
            arr
        }
        is ByteArray -> {
            // Encode as array of numbers
            val arr = jsNewArray()
            for (b in value) {
                jsArrayPush(arr, doubleToJsNumber((b.toInt() and 0xFF).toDouble()))
            }
            arr
        }
        else -> value.toString().toJsString()
    }
}

// --- Extension helpers for wasmJs types ---

private fun Int.toJsNumber(): JsNumber = doubleToJsNumber(this.toDouble())

@JsFun("(n) => n")
private external fun doubleToJsNumber(n: Double): JsNumber

@JsFun("(n) => +n")
private external fun jsNumberToDouble(n: JsNumber): Double

@JsFun("(n) => n|0")
private external fun jsNumberToInt(n: JsNumber): Int

private fun JsNumber.toDouble(): Double = jsNumberToDouble(this)
private fun JsNumber.toInt(): Int = jsNumberToInt(this)
