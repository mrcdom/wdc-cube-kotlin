package br.com.wdc.shopping.domain.security

import kotlinx.datetime.Instant

data class ChallengeResult(
    val nonce: String,
    val expiresAt: Instant,
)
