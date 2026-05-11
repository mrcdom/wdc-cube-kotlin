package br.com.wdc.framework.commons.security

import java.math.BigInteger
import java.util.Random

class RSA {

    var privateKey: BigInteger
        private set
    var publicExponent: BigInteger
        private set
    var publicKey: BigInteger
        private set

    constructor(n: Int, random: Random) {
        val p = BigInteger.probablePrime(n / 2, random)
        val q = BigInteger.probablePrime(n / 2, random)
        val phi = (p - BigInteger.ONE) * (q - BigInteger.ONE)

        publicKey = p * q
        publicExponent = N65537
        privateKey = publicExponent.modInverse(phi)
    }

    constructor(publicExponent: BigInteger, privateKey: BigInteger, publicKey: BigInteger) {
        this.publicExponent = publicExponent
        this.privateKey = privateKey
        this.publicKey = publicKey
    }

    fun encrypt(message: BigInteger): BigInteger = message.modPow(publicExponent, publicKey)

    fun decrypt(encrypted: BigInteger): BigInteger = encrypted.modPow(privateKey, publicKey)

    override fun toString(): String = buildString(256) {
        append("publicExponent  = ").append(publicExponent.toString(16)).append('\n')
        append("publicKey  = ").append(publicKey.toString(16)).append('\n')
        append("privateKey  = ").append(privateKey.toString(16)).append('\n')
    }

    companion object {
        @JvmField
        val N65537: BigInteger = BigInteger.valueOf(65537)
    }
}
