package br.com.wdc.shopping.domain.security

import kotlin.time.Instant

data class ChallengeResult(
    val nonce: String,
    val expiresAt: Instant,
)
