package br.com.wdc.shopping.persistence.security

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
}
