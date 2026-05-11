package br.com.wdc.shopping.domain.security

import java.util.concurrent.atomic.AtomicReference

interface AuthenticationService {

    fun challenge(): ChallengeResult

    fun login(userName: String, digest: String, nonce: String): AuthResult?

    fun refresh(refreshToken: String): AuthResult?

    fun logout(refreshToken: String)

    fun resolveToken(jwtToken: String): SecurityContext?

    companion object {
        val BEAN = AtomicReference<AuthenticationService>()
    }
}
