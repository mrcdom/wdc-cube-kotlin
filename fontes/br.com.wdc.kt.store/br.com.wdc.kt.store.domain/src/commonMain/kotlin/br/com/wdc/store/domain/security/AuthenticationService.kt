package br.com.wdc.store.domain.security

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.util.AtomicRef

/**
 * Signs users in and keeps their sessions. On the server it is the implementation itself; in a client, a proxy to
 * the server's `/api/auth`.
 */
interface AuthenticationService {

    /** Starts [userName]'s sign-in: a single-use nonce and the parameters of the user's verifier ([Scram]). */
    suspend fun challenge(userName: String): ChallengeResult

    /**
     * Ends the sign-in: [proof] is the client's SCRAM proof (Base64) for the [nonce] of a [challenge] made for
     * [userName]. `null` when the proof is wrong, the nonce is unknown, expired or someone else's, or the user does not
     * exist — the same answer for all. Too many failures for a user make it wait:
     * [br.com.wdc.framework.domain.exception.TooManyRequestsException], with the seconds to wait.
     */
    suspend fun login(userName: String, proof: String, nonce: String): AuthResult?

    fun refresh(refreshToken: String): AuthResult?

    fun logout(refreshToken: String)

    fun resolveToken(jwtToken: String): SecurityContext?

    // :: The session a client holds. The server holds none: there, these do nothing.

    fun writeAuthState(out: ExtensibleObjectOutput) {}

    fun readAuthState(input: ExtensibleObjectInput) {}

    /** Ends the session this client holds: the server revokes it, and the client forgets it. */
    fun signOut() {}

    /** Who hears when the session this client holds is renewed or lost; `null` for no one. */
    fun setSessionListener(listener: SessionListener?) {}

    companion object {
        val BEAN = AtomicRef<AuthenticationService>()
    }
}
