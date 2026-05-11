package br.com.wdc.shopping.domain.security

import java.time.Instant

data class ChallengeResult(
    val nonce: String,
    val expiresAt: Instant,
)
