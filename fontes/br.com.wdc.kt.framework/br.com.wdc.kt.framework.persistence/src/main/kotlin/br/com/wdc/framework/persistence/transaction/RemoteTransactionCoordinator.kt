package br.com.wdc.framework.persistence.transaction

/**
 * Coordenador de transações **dirigidas pelo cliente sobre REST** (lado servidor).
 *
 * Permite que o cliente demarque uma transação que atravessa **várias requisições HTTP** contra o mesmo
 * backend: o servidor mantém a transação física aberta entre as chamadas, identificada por um `txId`, e a
 * religa à thread que atende cada requisição.
 *
 * - [begin] — abre uma transação, mantém-na suspensa e devolve seu `txId`;
 * - [resume] / [suspend] — religa/desliga a transação na thread corrente (em torno de cada requisição que
 *   carrega o `txId`);
 * - [commit] / [rollback] — finalizam e removem do registro.
 *
 * **Dono (`ownerKey`):** uma chave **opaca** informada no [begin] e revalidada em todas as operações
 * seguintes — o coordenador apenas compara strings, sem conhecer o conceito de usuário. Quem traduz
 * identidade → chave (e o que fazer com `null`) é a camada chamadora.
 *
 * SPI **server-side de persistência**: consumido apenas pelo host REST, e por isso fora do domínio. Cada
 * `txId` mantém uma conexão viva até commit/rollback — daí a limpeza de transações abandonadas.
 */
interface RemoteTransactionCoordinator {

    /**
     * Abre uma transação (suspensa) e devolve seu identificador opaco.
     *
     * @param ownerKey chave opaca do dono (revalidada nas operações seguintes); `null` = sem dono
     */
    fun begin(ownerKey: String?): String

    /** Religa a transação à thread corrente. Falha se desconhecida, já em uso, ou se [ownerKey] divergir. */
    fun resume(txId: String, ownerKey: String?)

    /** Desliga a transação da thread corrente, mantendo-a viva para a próxima requisição. */
    fun suspend(txId: String)

    /**
     * Finaliza com COMMIT e remove do registro (valida [ownerKey]). **Idempotente**: repetir o commit de uma
     * transação já comitada (resposta anterior perdida) é um no-op de sucesso, dentro da janela de retenção
     * do desfecho. Repetir como rollback algo já comitado conflita.
     */
    fun commit(txId: String, ownerKey: String?)

    /** Finaliza com ROLLBACK e remove do registro (valida [ownerKey]). **Idempotente**, simétrico ao [commit]. */
    fun rollback(txId: String, ownerKey: String?)

    /**
     * Estado da transação para o solicitante, desambiguando uma resposta de finalização perdida.
     *
     * @return `"open"` (registrada e viva), `"committed"`/`"rolledback"` (finalizada, dentro da retenção) ou
     *         `"unknown"` (nunca existiu ou a retenção expirou). Valida [ownerKey].
     */
    fun status(txId: String, ownerKey: String?): String

    /** `true` se há transação registrada para [txId]. */
    fun exists(txId: String?): Boolean

    /** Snapshot de métricas (gauges atuais + contadores acumulados). */
    fun stats(): RemoteTransactionStats

    /**
     * `true` se há alguma transação remota aberta para este [ownerKey] (não-nulo). Permite ao host detectar
     * uma escrita que chegou **sem** o `txId` embora o solicitante tenha transação remota em aberto — sinal
     * de propagação quebrada do cabeçalho, que de outro modo autocommitaria fora da transação.
     */
    fun hasOpenTransactionForOwner(ownerKey: String?): Boolean
}
