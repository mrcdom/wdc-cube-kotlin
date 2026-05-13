package br.com.wdc.shopping.domain.security

import br.com.wdc.framework.commons.util.AtomicRef

interface CryptoProvider {

    fun md5(input: ByteArray): ByteArray

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    fun rsaEncryptOaep(publicKeyBase64: String, data: ByteArray): ByteArray

    companion object {
        val BEAN = AtomicRef<CryptoProvider>()
    }
}
