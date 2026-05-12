package br.com.wdc.framework.commons.gson

import com.google.gson.stream.JsonReader

class JsonReaderHelper(private val jr: JsonReader) {

    fun notImplemented(): Nothing = throw NotImplementedError()

    @Throws(java.io.IOException::class)
    fun `object`(actions: (MutableMap<String, () -> Unit>) -> Unit) {
        val actionMap = mutableMapOf<String, () -> Unit>()
        actions(actionMap)

        jr.beginObject()
        while (jr.hasNext()) {
            val name = jr.nextName()
            val action = actionMap[name]
            if (action != null) {
                action()
                continue
            }
            jr.skipValue()
        }
        jr.endObject()
    }

    @Throws(java.io.IOException::class)
    fun <E> arrayAsSet(parseJson: (JsonReader) -> E): LinkedHashSet<E> {
        val items = LinkedHashSet<E>()
        jr.beginArray()
        while (jr.hasNext()) {
            items.add(parseJson(jr))
        }
        jr.endArray()
        return items
    }

    @Throws(java.io.IOException::class)
    fun <E> arrayAsList(parseJson: (JsonReader) -> E): MutableList<E> {
        val items = mutableListOf<E>()
        jr.beginArray()
        while (jr.hasNext()) {
            items.add(parseJson(jr))
        }
        jr.endArray()
        return items
    }
}
