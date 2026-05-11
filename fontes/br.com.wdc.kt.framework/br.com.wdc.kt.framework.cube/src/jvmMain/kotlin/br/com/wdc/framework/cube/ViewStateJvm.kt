package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.gson.JsonExtensibleObjectOutput
import com.google.gson.stream.JsonWriter
import java.io.StringWriter

fun ViewState.toJson(instanceId: String): String {
    val strWriter = StringWriter()
    val json = JsonExtensibleObjectOutput(JsonWriter(strWriter))
    try {
        write(instanceId, json)
    } finally {
        json.flush()
    }
    return strWriter.toString()
}
