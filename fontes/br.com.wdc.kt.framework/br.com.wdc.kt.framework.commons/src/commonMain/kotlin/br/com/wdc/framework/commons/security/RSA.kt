package br.com.wdc.framework.commons.security

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

class RSA {

    var privateKey: BigInteger
        private set
    var publicExponent: BigInteger
        private set
    var publicKey: BigInteger
        private set

    constructor(publicExponent: BigInteger, privateKey: BigInteger, publicKey: BigInteger) {
        this.publicExponent = publicExponent
        this.privateKey = privateKey
        this.publicKey = publicKey
    }

    fun encrypt(message: BigInteger): BigInteger = modPow(message, publicExponent, publicKey)

    fun decrypt(encrypted: BigInteger): BigInteger = modPow(encrypted, privateKey, publicKey)

    override fun toString(): String = buildString(256) {
        append("publicExponent  = ").append(publicExponent.toString(16)).append('\n')
        append("publicKey  = ").append(publicKey.toString(16)).append('\n')
        append("privateKey  = ").append(privateKey.toString(16)).append('\n')
    }

    companion object {
        val N65537: BigInteger = BigInteger.parseString("65537", 10)

        /**
         * Modular exponentiation using square-and-multiply algorithm.
         */
        fun modPow(base: BigInteger, exponent: BigInteger, modulus: BigInteger): BigInteger {
            if (modulus == BigInteger.ONE) return BigInteger.ZERO

            var result = BigInteger.ONE
            var b = base mod modulus
            val expBytes = exponent.toByteArray()

            for (byte in expBytes) {
                val unsigned = byte.toInt() and 0xFF
                for (bit in 7 downTo 0) {
                    result = (result * result) mod modulus
                    if ((unsigned shr bit) and 1 == 1) {
                        result = (result * b) mod modulus
                    }
                }
            }
            return result
        }
    }
}
