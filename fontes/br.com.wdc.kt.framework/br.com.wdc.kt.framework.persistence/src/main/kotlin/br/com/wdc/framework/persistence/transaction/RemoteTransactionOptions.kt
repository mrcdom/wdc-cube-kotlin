package br.com.wdc.framework.persistence.transaction

/**
 * Parâmetros operacionais do [RemoteTransactionCoordinatorImpl], externalizáveis via configuração.
 *
 * @property idleTimeoutMs      tempo máximo ocioso antes de uma transação remota ser revertida e removida
 * @property maxLifetimeMs      tempo de vida **absoluto** desde a abertura: além disto a transação é revertida
 *                              mesmo que ativa, impedindo um cliente que "pinga" antes de cada idle-timeout de
 *                              segurar uma conexão indefinidamente
 * @property outcomeRetentionMs janela em que o desfecho de uma transação finalizada é lembrado, para tornar
 *                              commit/rollback idempotentes
 * @property maxOpen            teto global de transações remotas abertas (cada uma segura uma conexão)
 * @property maxOpenPerOwner    teto de transações remotas abertas por dono
 */
data class RemoteTransactionOptions(
    val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    val maxLifetimeMs: Long = DEFAULT_MAX_LIFETIME_MS,
    val outcomeRetentionMs: Long = DEFAULT_OUTCOME_RETENTION_MS,
    val maxOpen: Int = DEFAULT_MAX_OPEN,
    val maxOpenPerOwner: Int = DEFAULT_MAX_OPEN_PER_OWNER,
) {
    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS = 60_000L
        const val DEFAULT_MAX_LIFETIME_MS = 600_000L
        const val DEFAULT_OUTCOME_RETENTION_MS = 300_000L
        const val DEFAULT_MAX_OPEN = 128
        const val DEFAULT_MAX_OPEN_PER_OWNER = 16

        /** Todos os parâmetros nos defaults. */
        fun defaults(): RemoteTransactionOptions = RemoteTransactionOptions()

        /**
         * Lê da configuração sob `<prefix>database.remoteTransaction.*`; durações em **segundos** (como
         * `database.pool.connectionTimeoutSeconds`). Chave ausente → default.
         *
         * @param getInt leitura de inteiro da configuração: `(chave, default) -> valor`
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
            )
        }
    }
}
