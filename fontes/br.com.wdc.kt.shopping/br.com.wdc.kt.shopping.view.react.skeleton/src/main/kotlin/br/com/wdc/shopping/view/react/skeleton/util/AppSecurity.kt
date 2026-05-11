package br.com.wdc.shopping.view.react.skeleton.util

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.security.RSA
import java.math.BigInteger
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object AppSecurity {

    private const val SHA256_WITH_RSA = "SHA256withRSA"

    lateinit var rsa: RSA
        private set
    lateinit var webKey: String
        private set

    private var signPublicKey: PublicKey? = null
    private var signPrivateKey: PrivateKey? = null

    init {
        val logger = Log.getLogger("AppSecurity")

        // Cipher RSA
        run {
            val sPublicExponent: String
            val sPublicKey: String
            var wdcPrivateKey = System.getProperty("wdc.web.private_key")
            var wdcPublicKey = System.getProperty("wdc.web.public_key")

            if (wdcPublicKey.isNullOrBlank() || wdcPrivateKey.isNullOrBlank()) {
                // Fallback : It is util for development purpose
                sPublicExponent = "1ekh"
                sPublicKey = "3n88eu224huxfvj7lndkkf4n2vye4lus611fecnoc57qod2m7d"
                wdcPrivateKey = "2n9arhz94hevkz4ge8vxwje5c37k7aqol1st01wvzln81u5m69"
                wdcPublicKey = "$sPublicExponent:$sPublicKey"
            } else {
                val parts = wdcPublicKey.split(":")
                sPublicExponent = parts[0]
                sPublicKey = parts[1]
            }

            val publicExponent = BigInteger(sPublicExponent, 36)
            val publicKey = BigInteger(sPublicKey, 36)
            val privateKey = BigInteger(wdcPrivateKey, 36)
            this.rsa = RSA(publicExponent, privateKey, publicKey)
            this.webKey = wdcPublicKey
        }

        // Initialize signature resources
        try {
            var pk = System.getProperty("wdc.sign.wdc.web.public_key")
            var pv = System.getProperty("wdc.sign.wdc.web.private_key")
            if (pk.isNullOrBlank() || pv.isNullOrBlank()) {
                // Fallback : It is util for development purpose
                pk = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIkDxriJZ2BLyg26A7hR-qzJPRSj33156sXy_r6JLa0NWz2uY1z9FwnQRtrU3CztutyAIhwyHaOxfMGWyvgFsokCAwEAAQ=="
                pv = "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAiQPGuIlnYEvKDboDuFH6rMk9FKPffXnqxfL-voktrQ1bPa5jXP0XCdBG2tTcLO263IAiHDIdo7F8wZbK-AWyiQIDAQABAkABpau1PygHILu4tTC0ZEsblbnhltdHxfPW2m_KGUVqXjg71xASB-0rctP7pu9qgOPaj_ltTki3xHXQX07QKnJZAiEAvxFzS6c6FqJ8LbrVta72W5i-pb3AkLAM-wyoPmAOOxsCIQC3k8lagaTvRvdlkLrfJZ3K4q4JcsUHG6M2h43P34SfKwIgYtC9ljTIYAhsvKHSAQKZusmGX-WA_9NtAzGKmafH9F0CIGVwnpUKio9F0bMn1Hs2GAliVPUXnFQfK4MYSH6Tbn9dAiEAimwgt_xSziP2RejiFY3_Ek6ROpRG6uL9s89NuaoGFvY="
            }

            prepareSignKeys(pk, pv)
        } catch (exn: Exception) {
            logger.error("Sign not initialized", exn)
        }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    private fun prepareSignKeys(publicKeyInBase64: String, privateKeyInBase64: String) {
        val b64Dec = Base64.getUrlDecoder()

        val publicBytes = b64Dec.decode(publicKeyInBase64)
        val keySpecPublic = X509EncodedKeySpec(publicBytes)
        val keyFactoryPublic = KeyFactory.getInstance("RSA")
        this.signPublicKey = keyFactoryPublic.generatePublic(keySpecPublic)

        val privateBytes = b64Dec.decode(privateKeyInBase64)
        val keySpecPrivate = PKCS8EncodedKeySpec(privateBytes)
        val keyFactoryPrivate = KeyFactory.getInstance("RSA")
        this.signPrivateKey = keyFactoryPrivate.generatePrivate(keySpecPrivate)
    }

    fun sign(contentBytes: ByteArray): ByteArray {
        try {
            val sign = Signature.getInstance(SHA256_WITH_RSA)
            sign.initSign(signPrivateKey)
            sign.update(contentBytes)
            return sign.sign()
        } catch (exn: Exception) {
            throw RuntimeException(exn)
        }
    }

    fun signAsHash(contentBytes: ByteArray): ByteArray {
        try {
            val sign = Signature.getInstance(SHA256_WITH_RSA)
            sign.initSign(signPrivateKey)
            sign.update(contentBytes)
            val signature = sign.sign()

            val md5 = MessageDigest.getInstance("MD5")
            return md5.digest(signature)
        } catch (exn: Exception) {
            throw RuntimeException(exn)
        }
    }

    fun isThisSignatureValid(contentBytes: ByteArray, signature: ByteArray): Boolean {
        try {
            val verify = Signature.getInstance(SHA256_WITH_RSA)
            verify.initVerify(signPublicKey)
            verify.update(contentBytes)
            return verify.verify(signature)
        } catch (exn: Exception) {
            throw RuntimeException(exn)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val b64Enc = Base64.getUrlEncoder()
        val out = System.out

        // :: Exemplo de como gerar chaves para a cifragem
        run {
            val rsa = RSA(256, SecureRandom())
            val pk = rsa.publicExponent.toString(36) + ":" + rsa.publicKey.toString(36)
            val pv = rsa.publicKey.toString(36)
            out.println("<property name=\"wdc.web.public_key\" value=\"$pk\"/>")
            out.println("<property name=\"wdc.web.private_key\" value=\"$pv\"/>")
        }

        out.println()

        // :: Exemplo de como gerar as Chaves
        run {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(512)
            val pair = keyGen.generateKeyPair()
            val publicKey = pair.public
            val privateKey = pair.private

            val pk = b64Enc.encodeToString(publicKey.encoded)
            val pv = b64Enc.encodeToString(privateKey.encoded)
            out.println("<property name=\"wdc.sign.wdc.web.public_key\" value=\"$pk\"/>")
            out.println("<property name=\"wdc.sign.wdc.web.private_key\" value=\"$pv\"/>")
        }
    }
}
