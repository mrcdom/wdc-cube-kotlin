package br.com.wdc.framework.persistence.transaction

/**
 * Snapshot de observabilidade do [RemoteTransactionCoordinator]: **gauges** instantâneos (estado atual) e
 * **contadores** acumulados (totais desde o início).
 *
 * @property openNow          transações remotas abertas neste instante (cada uma segura uma conexão)
 * @property ownersWithOpen   donos distintos com ao menos uma transação aberta
 * @property retainedOutcomes desfechos finalizados ainda retidos (para idempotência)
 * @property begun            total de transações abertas
 * @property committed        total comitadas
 * @property rolledBack       total revertidas (por rollback explícito)
 * @property reaped           total revertidas pelo reaper (ociosidade ou tempo de vida — abandono)
 * @property rejectedByLimit  total de aberturas rejeitadas por teto (global ou por dono)
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
