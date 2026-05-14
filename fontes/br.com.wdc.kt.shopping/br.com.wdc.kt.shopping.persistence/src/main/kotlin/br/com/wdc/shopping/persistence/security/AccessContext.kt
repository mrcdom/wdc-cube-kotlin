package br.com.wdc.shopping.persistence.security

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.domain.security.SecurityContext
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant
import java.util.Base64

class AccessContext(
    override val userId: Long?,
    override val userName: String?,
    override val permissions: Set<String>,
    private val rsaKeyPair: KeyPair,
    val expiresAt: Instant,
    val refreshToken: String,
) : SecurityContext {

    override val privateKey: PrivateKey get() = rsaKeyPair.private

    val publicKey: PublicKey get() = rsaKeyPair.public

    override val publicKeyBase64: String
        get() = Base64.getEncoder().encodeToString(rsaKeyPair.public.encoded)

    val isExpired: Boolean get() = Instant.now().isAfter(expiresAt)

    override fun hasPermission(entity: String, operation: String): Boolean =
        permissions.contains("$entity:$operation") || permissions.contains("$entity:*")

    override fun hasDataAll(): Boolean = permissions.contains("data:all")

    override fun writeExternal(out: ExtensibleObjectOutput) {
        out.beginObject()
        userId?.let { out.name("userId").value(it) }
        userName?.let { out.name("userName").value(it) }
        out.name("publicKeyBase64").value(publicKeyBase64)
        if (permissions.isNotEmpty()) {
            out.name("permissions").beginArray()
            permissions.forEach { out.value(it) }
            out.endArray()
        }
        out.endObject()
    }

    override fun readExternal(input: ExtensibleObjectInput) {
        throw UnsupportedOperationException("AccessContext cannot be deserialized; use SimpleSecurityContext instead")
    }
}
