package br.com.wdc.shopping.domain.security

/**
 * CryptoProvider implementation for wasmJs using Web Crypto API.
 * For the client side, only md5 and hmacSha256 are typically needed.
 * rsaEncryptOaep is provided as a stub since RSA operations are handled server-side.
 */
class IosCryptoProvider : CryptoProvider {

    override fun md5(input: ByteArray): ByteArray {
        // Simple MD5 implementation in pure Kotlin for wasmJs
        return md5Hash(input)
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        return hmacSha256Compute(key, data)
    }

    override fun rsaEncryptOaep(publicKeyBase64: String, data: ByteArray): ByteArray {
        throw UnsupportedOperationException("RSA encryption is not supported in wasmJs client")
    }
}

// --- Pure Kotlin MD5 implementation ---

private fun md5Hash(message: ByteArray): ByteArray {
    val s = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    )

    val k = IntArray(64)
    for (i in 0 until 64) {
        k[i] = (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
    }

    var a0 = 0x67452301
    var b0 = 0xEFCDAB89.toInt()
    var c0 = 0x98BADCFE.toInt()
    var d0 = 0x10325476

    val originalLen = message.size
    val bitLen = originalLen.toLong() * 8

    // Pre-processing: padding
    val paddedLen = ((originalLen + 8) / 64 + 1) * 64
    val padded = ByteArray(paddedLen)
    message.copyInto(padded)
    padded[originalLen] = 0x80.toByte()

    // Append length in bits as 64-bit little-endian
    for (i in 0 until 8) {
        padded[paddedLen - 8 + i] = (bitLen ushr (i * 8)).toByte()
    }

    // Process each 512-bit (64-byte) chunk
    for (offset in 0 until paddedLen step 64) {
        val m = IntArray(16)
        for (j in 0 until 16) {
            m[j] = (padded[offset + j * 4].toInt() and 0xFF) or
                    ((padded[offset + j * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((padded[offset + j * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((padded[offset + j * 4 + 3].toInt() and 0xFF) shl 24)
        }

        var a = a0
        var b = b0
        var c = c0
        var d = d0

        for (i in 0 until 64) {
            val f: Int
            val g: Int
            when {
                i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
                i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                else   -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
            }
            val temp = d
            d = c
            c = b
            b = b + Integer_rotateLeft(a + f + k[i] + m[g], s[i])
            a = temp
        }

        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }

    val digest = ByteArray(16)
    for (i in 0 until 4) {
        digest[i] = (a0 ushr (i * 8)).toByte()
        digest[i + 4] = (b0 ushr (i * 8)).toByte()
        digest[i + 8] = (c0 ushr (i * 8)).toByte()
        digest[i + 12] = (d0 ushr (i * 8)).toByte()
    }
    return digest
}

private fun Integer_rotateLeft(value: Int, bits: Int): Int {
    return (value shl bits) or (value ushr (32 - bits))
}

// --- Pure Kotlin HMAC-SHA256 implementation ---

private fun hmacSha256Compute(key: ByteArray, data: ByteArray): ByteArray {
    val blockSize = 64
    val actualKey = when {
        key.size > blockSize -> sha256Hash(key)
        key.size < blockSize -> key + ByteArray(blockSize - key.size)
        else -> key.copyOf()
    }

    val oKeyPad = ByteArray(blockSize) { (actualKey[it].toInt() xor 0x5c).toByte() }
    val iKeyPad = ByteArray(blockSize) { (actualKey[it].toInt() xor 0x36).toByte() }

    val innerHash = sha256Hash(iKeyPad + data)
    return sha256Hash(oKeyPad + innerHash)
}

private fun sha256Hash(message: ByteArray): ByteArray {
    val k = longArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    )

    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19

    val originalLen = message.size
    val bitLen = originalLen.toLong() * 8

    val paddedLen = ((originalLen + 8) / 64 + 1) * 64
    val padded = ByteArray(paddedLen)
    message.copyInto(padded)
    padded[originalLen] = 0x80.toByte()

    // Append length in bits as 64-bit big-endian
    for (i in 0 until 8) {
        padded[paddedLen - 1 - i] = (bitLen ushr (i * 8)).toByte()
    }

    for (offset in 0 until paddedLen step 64) {
        val w = IntArray(64)
        for (j in 0 until 16) {
            w[j] = ((padded[offset + j * 4].toInt() and 0xFF) shl 24) or
                    ((padded[offset + j * 4 + 1].toInt() and 0xFF) shl 16) or
                    ((padded[offset + j * 4 + 2].toInt() and 0xFF) shl 8) or
                    (padded[offset + j * 4 + 3].toInt() and 0xFF)
        }
        for (j in 16 until 64) {
            val s0 = Integer_rotateRight(w[j - 15], 7) xor Integer_rotateRight(w[j - 15], 18) xor (w[j - 15] ushr 3)
            val s1 = Integer_rotateRight(w[j - 2], 17) xor Integer_rotateRight(w[j - 2], 19) xor (w[j - 2] ushr 10)
            w[j] = w[j - 16] + s0 + w[j - 7] + s1
        }

        var a = h0; var b = h1; var c = h2; var d = h3
        var e = h4; var f = h5; var g = h6; var h = h7

        for (j in 0 until 64) {
            val s1 = Integer_rotateRight(e, 6) xor Integer_rotateRight(e, 11) xor Integer_rotateRight(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + k[j].toInt() + w[j]
            val s0 = Integer_rotateRight(a, 2) xor Integer_rotateRight(a, 13) xor Integer_rotateRight(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }

        h0 += a; h1 += b; h2 += c; h3 += d
        h4 += e; h5 += f; h6 += g; h7 += h
    }

    val digest = ByteArray(32)
    for (i in 0 until 4) {
        digest[i] = (h0 ushr (24 - i * 8)).toByte()
        digest[i + 4] = (h1 ushr (24 - i * 8)).toByte()
        digest[i + 8] = (h2 ushr (24 - i * 8)).toByte()
        digest[i + 12] = (h3 ushr (24 - i * 8)).toByte()
        digest[i + 16] = (h4 ushr (24 - i * 8)).toByte()
        digest[i + 20] = (h5 ushr (24 - i * 8)).toByte()
        digest[i + 24] = (h6 ushr (24 - i * 8)).toByte()
        digest[i + 28] = (h7 ushr (24 - i * 8)).toByte()
    }
    return digest
}

private fun Integer_rotateRight(value: Int, bits: Int): Int {
    return (value ushr bits) or (value shl (32 - bits))
}
