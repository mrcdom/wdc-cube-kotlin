package br.com.wdc.store.domain.security

import br.com.wdc.framework.commons.serialization.Externalizable

/** The identity of an authenticated session: the user, the names of their roles and the session's RSA keys. */
interface SecurityContext : Externalizable {

    val userId: Long?

    val userName: String?

    /** The names of the user's roles ([Role]) — what the REST data policies grant by. */
    val roles: Set<String>

    val privateKey: PlatformPrivateKey?

    val publicKeyBase64: String?
}
