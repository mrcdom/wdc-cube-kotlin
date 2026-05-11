package br.com.wdc.framework.commons.serialization

import java.io.IOException

interface Externalizable {

    @Throws(IOException::class)
    fun writeExternal(out: ExtensibleObjectOutput)

    @Throws(IOException::class)
    fun readExternal(input: ExtensibleObjectInput)
}
