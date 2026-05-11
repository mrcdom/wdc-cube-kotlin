package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput

interface ViewState {

    fun write(instanceId: String, json: ExtensibleObjectOutput)
}
