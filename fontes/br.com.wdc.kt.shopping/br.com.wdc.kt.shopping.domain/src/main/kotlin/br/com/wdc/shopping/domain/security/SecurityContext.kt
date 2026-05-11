package br.com.wdc.shopping.domain.security

import java.security.PrivateKey

interface SecurityContext {

    val userId: Long?

    val userName: String?

    val permissions: Set<String>

    fun hasPermission(entity: String, operation: String): Boolean

    fun hasDataAll(): Boolean

    val privateKey: PrivateKey?

    val publicKeyBase64: String?
}
