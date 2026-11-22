package br.com.wdc.framework.persistence.transaction

/**
 * Operational parameters of [RemoteTransactionCoordinatorImpl], configurable externally.
 *
 * @property idleTimeoutMs      maximum idle time before a remote transaction is rolled back and removed
 * @property maxLifetimeMs      **absolute** lifetime since opening: past it the transaction is rolled back even
 *                              if active, preventing a client that "pings" before each idle timeout from
 *                              holding a connection indefinitely
 * @property outcomeRetentionMs window in which the outcome of a finished transaction is remembered, to make
 *                              commit/rollback idempotent
 * @property maxOpen            global cap of open remote transactions (each one holds a connection)
 * @property maxOpenPerOwner    cap of open remote transactions per owner
 * @property reapIntervalMs     interval of the periodic sweep of expired transactions
 *                              ([RemoteTransactionCoordinatorImpl.startReaper])
 */
data class RemoteTransactionOptions(
    val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    val maxLifetimeMs: Long = DEFAULT_MAX_LIFETIME_MS,
    val outcomeRetentionMs: Long = DEFAULT_OUTCOME_RETENTION_MS,
    val maxOpen: Int = DEFAULT_MAX_OPEN,
    val maxOpenPerOwner: Int = DEFAULT_MAX_OPEN_PER_OWNER,
    val reapIntervalMs: Long = DEFAULT_REAP_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS = 60_000L
        const val DEFAULT_MAX_LIFETIME_MS = 600_000L
        const val DEFAULT_OUTCOME_RETENTION_MS = 300_000L
        const val DEFAULT_MAX_OPEN = 128
        const val DEFAULT_MAX_OPEN_PER_OWNER = 16
        const val DEFAULT_REAP_INTERVAL_MS = 15_000L

        /** All parameters at their defaults. */
        fun defaults(): RemoteTransactionOptions = RemoteTransactionOptions()

        /**
         * Reads from the configuration under `<prefix>database.remoteTransaction.*`; durations in **seconds**
         * (like `database.pool.connectionTimeoutSeconds`). Missing key → default.
         *
         * @param getInt integer read from the configuration: `(key, default) -> value`
         */
        fun fromConfig(prefix: String, getInt: (key: String, defaultValue: Int) -> Int): RemoteTransactionOptions {
            val d = defaults()
            val base = "${prefix}database.remoteTransaction."
            return RemoteTransactionOptions(
                idleTimeoutMs = getInt(base + "idleTimeoutSeconds", (d.idleTimeoutMs / 1000).toInt()) * 1000L,
                maxLifetimeMs = getInt(base + "maxLifetimeSeconds", (d.maxLifetimeMs / 1000).toInt()) * 1000L,
                outcomeRetentionMs = getInt(base + "outcomeRetentionSeconds", (d.outcomeRetentionMs / 1000).toInt()) * 1000L,
                maxOpen = getInt(base + "maxOpen", d.maxOpen),
                maxOpenPerOwner = getInt(base + "maxOpenPerOwner", d.maxOpenPerOwner),
                reapIntervalMs = getInt(base + "reapIntervalSeconds", (d.reapIntervalMs / 1000).toInt()) * 1000L,
            )
        }
    }
}
