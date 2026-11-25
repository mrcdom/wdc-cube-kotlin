package br.com.wdc.store.persistence.security

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.store.domain.user.UserCriteria
import br.com.wdc.store.domain.user.User
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.domain.security.AuthResult
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.ChallengeResult
import br.com.wdc.store.domain.security.Role
import br.com.wdc.store.domain.security.SecurityContext
import br.com.wdc.framework.domain.projection.ProjectionValues
import kotlin.time.toKotlinInstant
import java.nio.charset.StandardCharsets
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.domain.security.Scram
import br.com.wdc.store.domain.security.ScramVerifier
import java.util.Base64
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs users in with SCRAM-SHA-256 ([Scram]) against the verifier each user has in the database, and keeps the
 * sessions ([AccessContextCache]).
 *
 * The answers do not tell whether a user exists: an unknown user gets a challenge with made-up parameters, the same
 * for the same name, and a refusal costs the same work. Failed sign-ins on one account are slowed down
 * ([LoginThrottle]), whichever way they come in.
 */
class AuthenticationServiceImpl(
    private val rawUserRepo: UserRepository,
    jwtSecret: String,
    refreshTokenTtlDays: Int = 7,
    private val throttle: LoginThrottle = LoginThrottle(),
) : AuthenticationService {

    companion object {
        private val LOG = Log.getLogger("AuthenticationServiceImpl")
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    private val cache = AccessContextCache(jwtSecret, IntentSecretStore(), refreshTokenTtlDays)
    private val nonceStore = NonceStore()
    private val scram = Scram(JceCryptoProvider())

    /** The key of the made-up salts: derived from the JWT secret, so they stay the same across restarts. */
    private val decoySaltKey = hmac("wdc-store-scram-decoy".toByteArray(StandardCharsets.UTF_8), jwtSecret.toByteArray(StandardCharsets.UTF_8))

    /** What an unknown user's proof is checked against, so a refusal takes the same work either way. */
    private val decoyVerifier = scram.verifier("decoy", ByteArray(ScramVerifier.SALT_SIZE), 1)

    override suspend fun challenge(userName: String): ChallengeResult {
        val verifier = ScramVerifier.parse(findUser(userName)?.password)
        val salt = verifier?.salt ?: decoySalt(userName)
        val iterations = verifier?.iterations ?: ScramVerifier.DEFAULT_ITERATIONS
        val nonce = nonceStore.generate(userName)
        val expiresAt = nonceStore.expiresAt(nonce)!!
        return ChallengeResult(nonce, Base64.getEncoder().encodeToString(salt), iterations, expiresAt.toKotlinInstant())
    }

    override suspend fun login(userName: String, proof: String, nonce: String): AuthResult? {
        throttle.check(userName)

        val user = findUser(userName)
        val verifier = ScramVerifier.parse(user?.password)
        val authMessage = Scram.authMessage(userName, nonce)
        val proofBytes = try {
            Base64.getDecoder().decode(proof)
        } catch (_: IllegalArgumentException) {
            ByteArray(0)
        }
        // the nonce and the proof are both checked, and the proof even without a user: the same work for every refusal
        val nonceOk = nonceStore.consume(nonce, userName)
        val proofOk = scram.verifyProof(verifier ?: decoyVerifier, authMessage, proofBytes)

        if (user == null || verifier == null || !nonceOk || !proofOk) {
            throttle.failed(userName)
            LOG.warn("Login refused: {}", if (!nonceOk) "invalid, expired or foreign nonce" else "wrong credentials")
            return null
        }
        throttle.succeeded(userName)

        val session = cache.createSession(user.id!!, user.userName!!, Role.names(Role.parse(user.roles)))
        val accessToken = JwtUtil.create(session.sessionId, user.id!!, user.userName!!, cache.accessTokenTtl, cache.jwtSecret)
        val serverSignature = Base64.getEncoder().encodeToString(scram.serverSignature(verifier.serverKey, authMessage))

        return AuthResult(
            user.id!!, accessToken, session.refreshToken, session.expiresAt.toKotlinInstant(),
            session.publicKeyBase64, session.intentSignSecret, serverSignature,
        )
    }

    override fun refresh(refreshToken: String): AuthResult? {
        val session = cache.refresh(refreshToken) ?: return null

        val accessToken = JwtUtil.create(session.sessionId, session.userId!!, session.userName!!,
            cache.accessTokenTtl, cache.jwtSecret)

        // a refresh proves nothing about the password: no server signature
        return AuthResult(session.userId!!, accessToken, session.refreshToken, session.expiresAt.toKotlinInstant(), session.publicKeyBase64, session.intentSignSecret, "")
    }

    override fun logout(refreshToken: String) {
        cache.removeByRefreshToken(refreshToken)
    }

    override fun resolveToken(jwtToken: String): SecurityContext? {
        val claims = JwtUtil.validate(jwtToken, cache.jwtSecret) ?: return null
        val sessionId = claims.sessionId ?: return null
        return cache.getBySessionId(sessionId)
    }

    /** The user's id, login, verifier and roles; `null` when there is no such user. */
    private suspend fun findUser(userName: String): User? {
        val pv = ProjectionValues
        val prj = User().apply {
            id = pv.i64
            this.userName = pv.str
            password = pv.str
            roles = pv.str
        }
        return rawUserRepo.fetch(UserCriteria().withUserName(userName).withProjection(prj), limit = 1).firstOrNull()
    }

    /** A made-up salt for a user that does not exist: the same for the same name, unrelated to any real one. */
    private fun decoySalt(userName: String): ByteArray =
        hmac(decoySaltKey, userName.trim().lowercase().toByteArray(StandardCharsets.UTF_8)).copyOf(ScramVerifier.SALT_SIZE)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).apply { init(SecretKeySpec(key, HMAC_ALGORITHM)) }.doFinal(data)

    /** Drops the expired sessions, the challenges nobody answered and the forgotten failures. */
    fun evictExpired() {
        cache.evictExpired()
        nonceStore.evictExpired()
        throttle.evictExpired()
    }

    /**
     * Runs [evictExpired] every [intervalSeconds] on [executor]: without it, the sessions and challenges that were
     * never used again would only pile up. Closing the result stops it.
     */
    fun startCleanup(executor: ScheduledExecutorService, intervalSeconds: Long = 60): AutoCloseable {
        val task = executor.scheduleWithFixedDelay(
            {
                try {
                    evictExpired()
                } catch (e: Exception) {
                    LOG.error("Evicting expired sessions", e)
                }
            },
            intervalSeconds, intervalSeconds, TimeUnit.SECONDS,
        )
        return AutoCloseable { task.cancel(false) }
    }
}
