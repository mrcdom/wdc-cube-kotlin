package br.com.wdc.store.domain.security

import kotlin.io.encoding.Base64

/**
 * The login's challenge and response: SCRAM-SHA-256 (RFC 5802, RFC 7677), the same scheme PostgreSQL uses.
 *
 * The server keeps only a [ScramVerifier] per user: a salt, an iteration count and two keys derived from the password.
 * Neither key lets anyone sign in, so a leak of the user table gives no access; guessing the password from it costs
 * one PBKDF2 run per guess. At sign-in the server sends the salt, the iteration count and a single-use nonce; the
 * client proves it knows the password without sending it ([clientProof]), and the server answers with its own
 * signature ([serverSignature]), proving it holds the verifier.
 *
 * The password goes in as its MD5 digest ([PasswordUtil.hashPassword]), the form the application stored before: that
 * is what lets a database migration turn the old digests into verifiers without knowing the passwords. The digest
 * adds nothing to the security, and takes nothing from it: what protects the password is the salted PBKDF2.
 *
 * Every computation goes through [crypto], so the same code runs on the server and in every client.
 */
class Scram(private val crypto: CryptoProvider) {

    /** `PBKDF2-HMAC-SHA256(password digest, salt, iterations)`, 32 bytes: the root of every key. */
    fun saltedPassword(passwordDigest: String, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations > 0) { "The iteration count must be positive" }
        val password = passwordDigest.encodeToByteArray()
        // a single block of PBKDF2: the key is as long as the hash
        var u = crypto.hmacSha256(password, salt + byteArrayOf(0, 0, 0, 1))
        val result = u.copyOf()
        repeat(iterations - 1) {
            u = crypto.hmacSha256(password, u)
            for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
        }
        return result
    }

    fun clientKey(saltedPassword: ByteArray): ByteArray = crypto.hmacSha256(saltedPassword, CLIENT_KEY)

    fun serverKey(saltedPassword: ByteArray): ByteArray = crypto.hmacSha256(saltedPassword, SERVER_KEY)

    fun storedKey(clientKey: ByteArray): ByteArray = crypto.sha256(clientKey)

    /** The verifier the server keeps for a password: what [ScramVerifier.create] stores. */
    fun verifier(passwordDigest: String, salt: ByteArray, iterations: Int): ScramVerifier {
        val salted = saltedPassword(passwordDigest, salt, iterations)
        return ScramVerifier(iterations, salt, storedKey(clientKey(salted)), serverKey(salted))
    }

    /** The client's proof: `ClientKey XOR HMAC(StoredKey, AuthMessage)`. It reveals neither the key nor the password. */
    fun clientProof(saltedPassword: ByteArray, authMessage: String): ByteArray {
        val clientKey = clientKey(saltedPassword)
        val signature = crypto.hmacSha256(storedKey(clientKey), authMessage.encodeToByteArray())
        return xor(clientKey, signature)
    }

    /** The server's signature over the exchange: only who holds the verifier can compute it. */
    fun serverSignature(serverKey: ByteArray, authMessage: String): ByteArray =
        crypto.hmacSha256(serverKey, authMessage.encodeToByteArray())

    /**
     * Whether [proof] shows the client knows the password of [verifier]: the key it carries, hashed, must be the stored
     * one. Compared in constant time.
     */
    fun verifyProof(verifier: ScramVerifier, authMessage: String, proof: ByteArray): Boolean {
        if (proof.size != verifier.storedKey.size) return false
        val signature = crypto.hmacSha256(verifier.storedKey, authMessage.encodeToByteArray())
        val clientKey = xor(proof, signature)
        return constantTimeEquals(storedKey(clientKey), verifier.storedKey)
    }

    /**
     * The client's whole side of the exchange, for the challenge the server sent: the proof to send and the server
     * signature to expect back.
     */
    fun respond(userName: String, password: String, challenge: ChallengeResult): ClientResponse {
        val salted = saltedPassword(PasswordUtil.hashPassword(password, crypto), b64Decode(challenge.salt), challenge.iterations)
        val authMessage = authMessage(userName, challenge.nonce)
        return ClientResponse(
            proof = Base64.encode(clientProof(salted, authMessage)),
            expectedServerSignature = Base64.encode(serverSignature(serverKey(salted), authMessage)),
        )
    }

    /** What the client sends ([proof]) and what it must get back from the server ([expectedServerSignature]). */
    class ClientResponse(val proof: String, val expectedServerSignature: String) {

        /** Whether the server's signature is the expected one, compared in constant time. */
        fun serverIsAuthentic(serverSignature: String): Boolean =
            constantTimeEquals(serverSignature.encodeToByteArray(), expectedServerSignature.encodeToByteArray())
    }

    companion object {

        private val CLIENT_KEY = "Client Key".encodeToByteArray()
        private val SERVER_KEY = "Server Key".encodeToByteArray()

        /** The provider set for the process. */
        fun current(): Scram = Scram(CryptoProvider.BEAN.get() ?: error("CryptoProvider.BEAN not initialized"))

        /** What both sides sign: the user and the server's nonce, so a proof is good for one user and one exchange. */
        fun authMessage(userName: String, nonce: String): String = "n=$userName,r=$nonce"

        /** Equality that takes the same time wherever the first difference is. */
        fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            return diff == 0
        }

        internal fun b64Decode(text: String): ByteArray = Base64.decode(text)

        private fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }
    }
}

/**
 * The stored form of a password: a SCRAM-SHA-256 verifier, in PostgreSQL's encoding —
 * `SCRAM-SHA-256$<iterations>:<salt>$<StoredKey>:<ServerKey>`, each binary part in Base64.
 */
class ScramVerifier(val iterations: Int, val salt: ByteArray, val storedKey: ByteArray, val serverKey: ByteArray) {

    fun encode(): String =
        "$MECHANISM\$$iterations:${Base64.encode(salt)}\$${Base64.encode(storedKey)}:${Base64.encode(serverKey)}"

    companion object {

        const val MECHANISM = "SCRAM-SHA-256"

        /**
         * The iteration count of new verifiers: RFC 7677's minimum and PostgreSQL's default. Every client derives the
         * key in its own code, on the user's device; a verifier keeps its own count, so it can be raised later.
         */
        const val DEFAULT_ITERATIONS = 4096

        /** Bytes of a new salt. */
        const val SALT_SIZE = 16

        /** Reads a stored verifier; `null` when [text] is not one. */
        fun parse(text: String?): ScramVerifier? {
            val parts = text?.trim()?.split('$') ?: return null
            if (parts.size != 3 || parts[0] != MECHANISM) return null
            val (iterations, salt) = parts[1].split(':').takeIf { it.size == 2 } ?: return null
            val (storedKey, serverKey) = parts[2].split(':').takeIf { it.size == 2 } ?: return null
            return try {
                ScramVerifier(iterations.toInt(), Base64.decode(salt), Base64.decode(storedKey), Base64.decode(serverKey))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
