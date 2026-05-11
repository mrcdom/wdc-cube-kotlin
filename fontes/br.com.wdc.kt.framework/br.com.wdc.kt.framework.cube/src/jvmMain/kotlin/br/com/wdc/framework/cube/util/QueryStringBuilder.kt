package br.com.wdc.framework.cube.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class QueryStringBuilder {

    private val sb = StringBuilder()

    fun append(parameters: Map<String, Any>) {
        for ((key, value) in parameters) {
            if (value is Array<*>) {
                for (element in value) {
                    if (element != null) {
                        appendPair(key, element.toString())
                    }
                }
            } else {
                appendPair(key, value.toString())
            }
        }
    }

    private fun appendPair(name: String, value: String) {
        if (sb.isNotEmpty()) {
            sb.append('&')
        }
        sb.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
        sb.append('=')
        sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8))
    }

    override fun toString(): String = sb.toString()
}
