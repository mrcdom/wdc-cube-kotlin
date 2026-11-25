package br.com.wdc.store.persistence.security

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics

/**
 * What sign-in and the sessions count, in Micrometer's global registry: nothing goes anywhere until the backend adds a
 * registry to it (Prometheus, at `/metrics`).
 */
internal object SecurityMeters {

    private fun signIns(outcome: String): Counter =
        Counter.builder("store.signins").description("Sign-in attempts, by outcome").tag("outcome", outcome)
            .register(Metrics.globalRegistry)

    /** Signed in. */
    val signedIn: Counter = signIns("ok")

    /** Wrong password, unknown user, or a bad nonce. */
    val refused: Counter = signIns("refused")

    /** Refused before checking, while the user must wait after too many failures. */
    val throttled: Counter = signIns("throttled")

    /** Sessions renewed by their refresh token. */
    val renewed: Counter = Counter.builder("store.sessions.renewed").description("Sessions renewed")
        .register(Metrics.globalRegistry)

    /** Sessions another instance said had ended. */
    val endedElsewhere: Counter = Counter.builder("store.sessions.ended.elsewhere")
        .description("Notices from other instances of a session that ended").register(Metrics.globalRegistry)
}
