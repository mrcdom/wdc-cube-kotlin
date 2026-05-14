package br.com.wdc.shopping.domain.security

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils

class SimpleSecurityContext : SecurityContext {

    override var userId: Long? = null
    override var userName: String? = null
    override var permissions: Set<String> = emptySet()
    override val privateKey: PlatformPrivateKey? = null
    override var publicKeyBase64: String? = null

    override fun hasPermission(entity: String, operation: String): Boolean =
        permissions.contains("$entity:$operation") || permissions.contains("$entity:*")

    override fun hasDataAll(): Boolean = permissions.contains("data:all")

    override fun writeExternal(out: ExtensibleObjectOutput) {
        out.beginObject()
        userId?.let { out.name("userId").value(it) }
        userName?.let { out.name("userName").value(it) }
        publicKeyBase64?.let { out.name("publicKeyBase64").value(it) }
        if (permissions.isNotEmpty()) {
            out.name("permissions").beginArray()
            permissions.forEach { out.value(it) }
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
                "permissions" -> {
                    val perms = mutableSetOf<String>()
                    input.beginArray()
                    while (input.hasNext()) {
                        perms.add(input.nextString())
                    }
                    input.endArray()
                    permissions = perms
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
    }
}
