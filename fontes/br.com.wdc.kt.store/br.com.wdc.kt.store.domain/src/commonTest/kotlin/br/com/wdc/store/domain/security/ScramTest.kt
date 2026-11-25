package br.com.wdc.store.domain.security

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

expect fun platformCryptoProvider(): CryptoProvider

/** SCRAM-SHA-256 on each platform's crypto: the RFC 7677 test vector, and the verifier the server keeps. */
class ScramTest {

    private val scram = Scram(platformCryptoProvider())

    @Test
    fun rfc7677TestVector() {
        // RFC 7677, section 3: user "user", password "pencil"
        val salt = Base64.decode("W22ZaJ0SNY7soEsUEjb6gQ==")
        val authMessage = "n=user,r=rOprNGfwEbeRWgbNEkqO," +
            "r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF\$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096," +
            "c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF\$k0"
        val salted = scram.saltedPassword("pencil", salt, 4096)

        assertEquals("dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=", Base64.encode(scram.clientProof(salted, authMessage)))
        assertEquals("6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=", Base64.encode(scram.serverSignature(scram.serverKey(salted), authMessage)))
    }

    @Test
    fun theVerifierAcceptsTheRightPassword_only() {
        val salt = ByteArray(ScramVerifier.SALT_SIZE) { it.toByte() }
        val verifier = scram.verifier("digest-of-secret", salt, 64)
        val authMessage = Scram.authMessage("john", "nonce-1")

        val right = scram.clientProof(scram.saltedPassword("digest-of-secret", salt, 64), authMessage)
        val wrong = scram.clientProof(scram.saltedPassword("digest-of-other", salt, 64), authMessage)
        assertTrue(scram.verifyProof(verifier, authMessage, right))
        assertFalse(scram.verifyProof(verifier, authMessage, wrong))
        assertFalse(scram.verifyProof(verifier, Scram.authMessage("john", "nonce-2"), right), "a proof is good for one exchange")
        assertFalse(scram.verifyProof(verifier, Scram.authMessage("jane", "nonce-1"), right), "and for one user")
    }

    @Test
    fun theClientRecognizesTheServer_byItsSignature() {
        val salt = ByteArray(ScramVerifier.SALT_SIZE) { (it * 7).toByte() }
        val digest = PasswordUtil.hashPassword("secret", platformCryptoProvider())
        val verifier = scram.verifier(digest, salt, 64)
        val challenge = ChallengeResult("nonce-1", Base64.encode(salt), 64, kotlin.time.Instant.DISTANT_FUTURE)

        val response = scram.respond("john", "secret", challenge)
        val authMessage = Scram.authMessage("john", "nonce-1")
        assertTrue(scram.verifyProof(verifier, authMessage, Base64.decode(response.proof)))
        assertTrue(response.serverIsAuthentic(Base64.encode(scram.serverSignature(verifier.serverKey, authMessage))))
        assertFalse(response.serverIsAuthentic(Base64.encode(ByteArray(32))))
    }

    @Test
    fun aVerifierIsStoredInPostgresqlsEncoding_andReadBack() {
        val verifier = scram.verifier("digest", ByteArray(ScramVerifier.SALT_SIZE) { 1 }, 4096)
        val text = verifier.encode()
        assertTrue(text.startsWith("SCRAM-SHA-256\$4096:"), text)

        val read = assertNotNull(ScramVerifier.parse(text))
        assertEquals(4096, read.iterations)
        assertTrue(read.storedKey.contentEquals(verifier.storedKey) && read.serverKey.contentEquals(verifier.serverKey))
        assertNull(ScramVerifier.parse("5f4dcc3b5aa765d61d8327deb882cf99"), "an old digest is not a verifier")
        assertNull(ScramVerifier.parse(null))
    }
}
