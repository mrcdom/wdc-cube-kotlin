package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.JsonOutputFactory

fun ViewState.toJson(instanceId: String): String {
    val jsonOutput = JsonOutputFactory.createStringOutput()
    write(instanceId, jsonOutput.output)
    return jsonOutput.resultString()
}
