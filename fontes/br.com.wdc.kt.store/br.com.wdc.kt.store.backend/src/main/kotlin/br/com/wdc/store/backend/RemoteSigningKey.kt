package br.com.wdc.store.backend

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The key that signs the remote view's session ids and addresses (`RemoteAppSecurity.useKey`), derived from the JWT
 * secret: every instance with the same configuration signs with the same key, so a page served by one instance and its
 * socket on another accept each other's session id. HMAC-SHA256 of a label of its own under the secret, so this key
 * reveals nothing about the secret, and no other key derived from it is this one.
 */
internal object RemoteSigningKey {

    private const val LABEL = "wdc-store/remote-session-ids/v1"

    /** 32 bytes, the same for the same [jwtSecret]. */
    fun from(jwtSecret: String): ByteArray =
        Mac.getInstance("HmacSHA256")
            .apply { init(SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")) }
            .doFinal(LABEL.toByteArray(StandardCharsets.UTF_8))
}
