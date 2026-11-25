package br.com.wdc.store.domain.security

import kotlin.time.Instant

data class AuthResult(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val publicKey: String,
    val intentSignKey: String,
    /** The server's SCRAM signature (Base64): the client checks it to know the server holds its verifier. */
    val serverSignature: String,
)
