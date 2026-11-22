package br.com.wdc.framework.persistence.transaction

/**
 * Observability snapshot of [RemoteTransactionCoordinator]: instantaneous **gauges** (current state) and
 * accumulated **counters** (totals since start).
 *
 * @property openNow          remote transactions open at this instant (each one holds a connection)
 * @property ownersWithOpen   distinct owners with at least one open transaction
 * @property retainedOutcomes finished outcomes still retained (for idempotency)
 * @property begun            total transactions opened
 * @property committed        total committed
 * @property rolledBack       total rolled back (by explicit rollback)
 * @property reaped           total rolled back by the reaper (idleness or lifetime — abandonment)
 * @property rejectedByLimit  total openings rejected by a cap (global or per owner)
 */
data class RemoteTransactionStats(
    val openNow: Int,
    val ownersWithOpen: Int,
    val retainedOutcomes: Int,
    val begun: Long,
    val committed: Long,
    val rolledBack: Long,
    val reaped: Long,
    val rejectedByLimit: Long,
)
