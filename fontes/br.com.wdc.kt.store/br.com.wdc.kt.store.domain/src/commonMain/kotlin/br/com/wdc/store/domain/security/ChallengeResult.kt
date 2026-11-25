package br.com.wdc.store.domain.security

import kotlin.time.Instant

/**
 * The server's challenge for one user's sign-in: a single-use [nonce] and the parameters of the user's verifier — the
 * [salt] (Base64) and the [iterations] — so the client derives the same key. An unknown user gets made-up parameters,
 * the same every time, so the answer does not tell whether the user exists.
 */
data class ChallengeResult(
    val nonce: String,
    val salt: String,
    val iterations: Int,
    val expiresAt: Instant,
)
