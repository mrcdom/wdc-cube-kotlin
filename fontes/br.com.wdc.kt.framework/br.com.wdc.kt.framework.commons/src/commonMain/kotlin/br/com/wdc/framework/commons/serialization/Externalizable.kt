package br.com.wdc.framework.commons.serialization

interface Externalizable {

    @Throws(Exception::class)
    fun writeExternal(out: ExtensibleObjectOutput)

    @Throws(Exception::class)
    fun readExternal(input: ExtensibleObjectInput)
}
