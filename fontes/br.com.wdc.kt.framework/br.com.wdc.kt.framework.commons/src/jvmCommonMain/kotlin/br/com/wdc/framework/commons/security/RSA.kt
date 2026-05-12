package br.com.wdc.framework.commons.security

import com.ionspin.kotlin.bignum.integer.BigInteger
import java.util.Random

/**
 * Factory to generate RSA keys using probable primes (JVM only).
 */
fun RSA.Companion.generate(n: Int, random: Random): RSA {
    val p = java.math.BigInteger.probablePrime(n / 2, random)
    val q = java.math.BigInteger.probablePrime(n / 2, random)
    val phi = (p - java.math.BigInteger.ONE) * (q - java.math.BigInteger.ONE)

    val jPublicKey = p * q
    val jPublicExponent = java.math.BigInteger.valueOf(65537)
    val jPrivateKey = jPublicExponent.modInverse(phi)

    return RSA(
        publicExponent = BigInteger.parseString(jPublicExponent.toString(16), 16),
        privateKey = BigInteger.parseString(jPrivateKey.toString(16), 16),
        publicKey = BigInteger.parseString(jPublicKey.toString(16), 16)
    )
}
