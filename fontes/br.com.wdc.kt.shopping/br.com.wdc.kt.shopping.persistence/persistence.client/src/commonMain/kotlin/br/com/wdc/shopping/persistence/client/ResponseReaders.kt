package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput

/** Reads {"success": true/false, ...} from the input. */
internal fun readSuccess(input: ExtensibleObjectInput): Boolean {
    var success = false
    input.beginObject()
    while (input.hasNext()) {
        when (input.nextName()) {
            "success" -> success = input.nextBoolean()
            else -> input.skipValue()
        }
    }
    input.endObject()
    return success
}

/** Reads {"count": N, ...} from the input. */
internal fun readCount(input: ExtensibleObjectInput): Int {
    var count = 0
    input.beginObject()
    while (input.hasNext()) {
        when (input.nextName()) {
            "count" -> count = input.nextInt()
            else -> input.skipValue()
        }
    }
    input.endObject()
    return count
}
