package br.com.wdc.shopping.domain.security

import java.util.concurrent.atomic.AtomicReference

interface CryptoProvider {

    fun md5(input: ByteArray): ByteArray

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    companion object {
        val BEAN = AtomicReference<CryptoProvider>()
    }
}
