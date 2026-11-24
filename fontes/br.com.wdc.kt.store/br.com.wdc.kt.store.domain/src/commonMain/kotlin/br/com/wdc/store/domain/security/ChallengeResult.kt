package br.com.wdc.store.domain.security

import kotlin.time.Instant

data class ChallengeResult(
    val nonce: String,
    val expiresAt: Instant,
)
