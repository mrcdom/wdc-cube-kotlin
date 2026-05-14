package br.com.wdc.shopping.domain.security

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.util.AtomicRef

interface AuthenticationService {

    fun challenge(): ChallengeResult

    fun login(userName: String, digest: String, nonce: String): AuthResult?

    fun refresh(refreshToken: String): AuthResult?

    fun logout(refreshToken: String)

    fun resolveToken(jwtToken: String): SecurityContext?

    fun writeAuthState(out: ExtensibleObjectOutput) {}

    fun readAuthState(input: ExtensibleObjectInput) {}

    companion object {
        val BEAN = AtomicRef<AuthenticationService>()
    }
}
