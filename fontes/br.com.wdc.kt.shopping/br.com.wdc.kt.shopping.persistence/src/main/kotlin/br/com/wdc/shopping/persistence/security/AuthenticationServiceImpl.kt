package br.com.wdc.shopping.persistence.security

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.AuthResult
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.ChallengeResult
import br.com.wdc.shopping.domain.security.Role
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.utils.ProjectionValues
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AuthenticationServiceImpl(
    private val rawUserRepo: UserRepository,
    jwtSecret: String,
) : AuthenticationService {

    companion object {
        private val LOG = Log.getLogger("AuthenticationServiceImpl")
        private const val HMAC_ALGORITHM = "HmacSHA256"

        private fun computeHmac(key: String, data: String): String {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
            val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            return bytesToHex(hash)
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }
    }

    private val cache = AccessContextCache(jwtSecret)
    private val nonceStore = NonceStore()

    override fun challenge(): ChallengeResult {
        val nonce = nonceStore.generate()
        val expiresAt = nonceStore.expiresAt(nonce)!!
        return ChallengeResult(nonce, expiresAt)
    }

    override fun login(userName: String, digest: String, nonce: String): AuthResult? {
        if (!nonceStore.consume(nonce)) {
            LOG.warn("Login failed: invalid or expired nonce")
            return null
        }

        val user = fetchUserForAuth(userName)
        if (user == null) {
            LOG.warn("Login failed: user not found: {}", userName)
            return null
        }

        val expectedDigest = computeHmac(user.password!!, userName + nonce)

        if (!MessageDigest.isEqual(
                expectedDigest.toByteArray(StandardCharsets.UTF_8),
                digest.toByteArray(StandardCharsets.UTF_8))) {
            LOG.warn("Login failed: HMAC mismatch for user: {}", userName)
            return null
        }

        val roles = Role.parse(user.roles)
        val permissions = Role.effectivePermissions(roles)
        val session = cache.createSession(user.id!!, user.userName!!, permissions)
        val accessToken = JwtUtil.create(user.id!!, user.userName!!, cache.accessTokenTtl, cache.jwtSecret)

        return AuthResult(user.id!!, accessToken, session.refreshToken, session.expiresAt, session.publicKeyBase64)
    }

    override fun refresh(refreshToken: String): AuthResult? {
        val session = cache.refresh(refreshToken) ?: return null

        val accessToken = JwtUtil.create(session.userId!!, session.userName!!,
            cache.accessTokenTtl, cache.jwtSecret)

        return AuthResult(session.userId!!, accessToken, session.refreshToken, session.expiresAt, session.publicKeyBase64)
    }

    override fun logout(refreshToken: String) {
        cache.removeByRefreshToken(refreshToken)
    }

    override fun resolveToken(jwtToken: String): SecurityContext? {
        val claims = JwtUtil.validate(jwtToken, cache.jwtSecret) ?: return null
        return cache.get(claims.userId)
    }

    private fun fetchUserForAuth(userName: String): User? {
        val pv = ProjectionValues
        val prj = User()
        prj.id = pv.i64
        prj.userName = pv.str
        prj.password = pv.str
        prj.roles = pv.str

        val users = rawUserRepo.fetch(UserCriteria()
            .withUserName(userName)
            .withProjection(prj)
            .withLimit(1))

        if (users.isEmpty()) return null

        val user = users[0]
        user.password = user.password?.trim()
        return user
    }
}
