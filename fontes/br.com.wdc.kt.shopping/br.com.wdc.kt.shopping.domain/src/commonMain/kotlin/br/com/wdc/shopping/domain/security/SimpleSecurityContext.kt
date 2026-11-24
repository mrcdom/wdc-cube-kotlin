package br.com.wdc.shopping.domain.security

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils

class SimpleSecurityContext : SecurityContext {

    override var userId: Long? = null
    override var userName: String? = null
    override var roles: Set<String> = emptySet()
    override val privateKey: PlatformPrivateKey? = null
    override var publicKeyBase64: String? = null

    override fun writeExternal(out: ExtensibleObjectOutput) {
        out.beginObject()
        userId?.let { out.name("userId").value(it) }
        userName?.let { out.name("userName").value(it) }
        publicKeyBase64?.let { out.name("publicKeyBase64").value(it) }
        if (roles.isNotEmpty()) {
            out.name("roles").beginArray()
            roles.forEach { out.value(it) }
            out.endArray()
        }
        out.endObject()
    }

    override fun readExternal(input: ExtensibleObjectInput) {
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "userId" -> userId = InputCoerceUtils.asLong(input)
                "userName" -> userName = InputCoerceUtils.asString(input)
                "publicKeyBase64" -> publicKeyBase64 = InputCoerceUtils.asString(input)
                "roles" -> {
                    val names = mutableSetOf<String>()
                    input.beginArray()
                    while (input.hasNext()) {
                        names.add(input.nextString())
                    }
                    input.endArray()
                    roles = names
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
    }
}
