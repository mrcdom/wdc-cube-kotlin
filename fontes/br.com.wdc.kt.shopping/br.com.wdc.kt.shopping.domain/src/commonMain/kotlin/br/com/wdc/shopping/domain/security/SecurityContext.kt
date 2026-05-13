package br.com.wdc.shopping.domain.security

interface SecurityContext {

    val userId: Long?

    val userName: String?

    val permissions: Set<String>

    fun hasPermission(entity: String, operation: String): Boolean

    fun hasDataAll(): Boolean

    val privateKey: PlatformPrivateKey?

    val publicKeyBase64: String?
}
