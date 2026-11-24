package br.com.wdc.store.domain.security

import kotlin.time.Instant

data class AuthResult(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val publicKey: String,
    val intentSignKey: String,
)
