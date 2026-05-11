package br.com.wdc.shopping.persistence.security

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object JwtUtil {

    private const val ALGORITHM = "HmacSHA256"
    private val B64_ENC = Base64.getUrlEncoder().withoutPadding()
    private val B64_DEC = Base64.getUrlDecoder()

    private const val HEADER_JSON = """{"alg":"HS256","typ":"JWT"}"""
    private val ENCODED_HEADER = B64_ENC.encodeToString(HEADER_JSON.toByteArray(StandardCharsets.UTF_8))

    fun create(userId: Long, userName: String, ttl: Duration, secret: String): String {
        val now = Instant.now()
        val exp = now.plus(ttl)

        val payload = JsonObject()
        payload.addProperty("sub", userId)
        payload.addProperty("usr", userName)
        payload.addProperty("iat", now.epochSecond)
        payload.addProperty("exp", exp.epochSecond)

        val encodedPayload = B64_ENC.encodeToString(payload.toString().toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$ENCODED_HEADER.$encodedPayload"
        val signature = sign(signingInput, secret)

        return "$signingInput.$signature"
    }

    fun validate(token: String?, secret: String): Claims? {
        if (token.isNullOrBlank()) return null

        val parts = token.split(".")
        if (parts.size != 3) return null

        val signingInput = "${parts[0]}.${parts[1]}"
        val expectedSignature = sign(signingInput, secret)

        if (!MessageDigest.isEqual(
                expectedSignature.toByteArray(StandardCharsets.UTF_8),
                parts[2].toByteArray(StandardCharsets.UTF_8))) {
            return null
        }

        return try {
            val payloadJson = String(B64_DEC.decode(parts[1]), StandardCharsets.UTF_8)
            val payload = JsonParser.parseString(payloadJson).asJsonObject

            val exp = payload.get("exp").asLong
            if (Instant.now().epochSecond > exp) return null

            Claims(
                userId = payload.get("sub").asLong,
                userName = if (payload.has("usr")) payload.get("usr").asString else null,
                expiresAt = Instant.ofEpochSecond(exp),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sign(data: String, secret: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM))
        return B64_ENC.encodeToString(mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)))
    }

    data class Claims(val userId: Long, val userName: String?, val expiresAt: Instant)
}
