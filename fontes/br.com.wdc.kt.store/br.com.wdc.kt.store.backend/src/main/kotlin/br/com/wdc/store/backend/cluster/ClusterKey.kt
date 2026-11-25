package br.com.wdc.store.backend.cluster

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The key the instances of the mesh prove to each other ([ClusterMesh]), derived from the JWT secret: the instances of
 * one configuration share it, and nothing else does. HMAC-SHA256 of a label of its own under the secret, so this key
 * reveals nothing about the secret, and no other key derived from it is this one.
 */
internal object ClusterKey {

    private const val LABEL = "wdc-store/cluster-mesh/v1"

    /** 32 bytes, the same for the same [jwtSecret]. */
    fun from(jwtSecret: String): ByteArray =
        Mac.getInstance("HmacSHA256")
            .apply { init(SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")) }
            .doFinal(LABEL.toByteArray(StandardCharsets.UTF_8))
}
