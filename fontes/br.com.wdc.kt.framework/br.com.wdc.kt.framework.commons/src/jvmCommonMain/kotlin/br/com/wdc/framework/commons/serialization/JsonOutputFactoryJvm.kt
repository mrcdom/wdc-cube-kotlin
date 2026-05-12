package br.com.wdc.framework.commons.serialization

import br.com.wdc.framework.commons.gson.JsonExtensibleObjectOutput
import com.google.gson.stream.JsonWriter
import java.io.StringWriter

fun JsonOutputFactory.installGson() {
    install {
        val strWriter = StringWriter()
        val output = JsonExtensibleObjectOutput(JsonWriter(strWriter))
        JsonStringOutput(output) {
            output.flush()
            strWriter.toString()
        }
    }
}
