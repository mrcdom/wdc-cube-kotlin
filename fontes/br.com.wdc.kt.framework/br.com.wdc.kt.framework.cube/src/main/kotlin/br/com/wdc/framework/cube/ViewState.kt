package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.gson.JsonExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import com.google.gson.stream.JsonWriter
import java.io.StringWriter

interface ViewState {

    fun write(instanceId: String, json: ExtensibleObjectOutput)

    fun toJson(instanceId: String): String {
        val strWriter = StringWriter()
        val json = JsonExtensibleObjectOutput(JsonWriter(strWriter))
        try {
            write(instanceId, json)
        } finally {
            json.flush()
        }
        return strWriter.toString()
    }
}
