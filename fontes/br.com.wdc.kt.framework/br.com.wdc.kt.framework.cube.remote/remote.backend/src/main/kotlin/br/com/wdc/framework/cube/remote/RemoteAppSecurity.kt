package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.codec.Base62
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The host's signatures: HMAC-SHA256 with a secret key that only the server holds. It signs what the client gets
 * back and must not forge — the session id ([newSessionId], checked by [isSessionId]) and the navigation addresses
 * ([sign]). Only the server signs and checks, so there is no key pair and nothing public.
 *
 * Without [useKey], the key is random, made when the process starts: enough while the sessions live in this process
 * (a restart ends them anyway). Several instances behind one address need the same key: give each the same secret.
 */
object RemoteAppSecurity {

    private const val ALGORITHM = "HmacSHA256"

    /** Bytes of the random part of a session id. */
    private const val SESSION_ID_BYTES = 32

    private val random = SecureRandom()

    @Volatile
    private var key: SecretKeySpec = SecretKeySpec(ByteArray(32).also(random::nextBytes), ALGORITHM)

    /** Signs with [secret] from now on (at least 32 bytes): the same on every instance that serves the same clients. */
    fun useKey(secret: ByteArray) {
        require(secret.size >= 32) { "The signing key must have at least 32 bytes" }
        key = SecretKeySpec(secret.copyOf(), ALGORITHM)
    }

    /** The signature of [text], in Base62. */
    fun sign(text: String): String = Base62.encodeToString(hmac(text.toByteArray(StandardCharsets.UTF_8)))

    /** Whether [signature] is this host's signature of [text], compared in constant time. */
    fun verify(text: String, signature: String): Boolean =
        MessageDigest.isEqual(sign(text).toByteArray(StandardCharsets.UTF_8), signature.toByteArray(StandardCharsets.UTF_8))

    /** A new session id: a random part and its signature, `<random>.<signature>`, both Base62. */
    fun newSessionId(): String {
        val part = Base62.encodeToString(ByteArray(SESSION_ID_BYTES).also(random::nextBytes))
        return "$part.${sign(part)}"
    }

    /** Whether [id] is a session id this host made ([newSessionId]). */
    fun isSessionId(id: String): Boolean {
        val parts = id.split('.')
        return parts.size == 2 && parts[0].isNotEmpty() && verify(parts[0], parts[1])
    }

    private fun hmac(data: ByteArray): ByteArray = Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(data)
}
