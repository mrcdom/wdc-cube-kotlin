package br.com.wdc.framework.commons.serialization

import br.com.wdc.framework.commons.gson.JsonExtensibleObjectInput
import com.google.gson.stream.JsonReader
import java.io.StringReader

fun JsonInputFactory.installGson() {
    install { json ->
        val reader = JsonExtensibleObjectInput(JsonReader(StringReader(json)))
        JsonStringInput(reader)
    }
}
