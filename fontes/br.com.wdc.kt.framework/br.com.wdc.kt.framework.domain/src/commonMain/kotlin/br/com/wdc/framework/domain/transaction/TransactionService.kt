package br.com.wdc.framework.domain.transaction

/**
 * Demarcação de transação no estilo CMT: o caso de uso declara a propagação e o serviço cuida de
 * abrir, confirmar e desfazer.
 *
 * Em todas as propagações: **retorno normal → commit** (salvo [TransactionContext.setRollbackOnly]);
 * **qualquer exceção → rollback, e a exceção original é repropagada intacta**; falha ao finalizar →
 * [TransactionSystemException].
 *
 * O contrato vale igual no servidor (transação JDBC) e no cliente (transação remota via REST).
 *
 * **O bloco transacional não deve fazer suspensão real** — `delay`, troca de dispatcher, espera de rede
 * alheia à transação. No servidor a transação segura uma conexão do pool durante a espera; no cliente, as
 * chamadas de uma transação remota precisam ser serializadas. As implementações `suspend` dos repositórios
 * respeitam isso.
 */
interface TransactionService {

    /** Participa da transação corrente; se não houver, abre uma. */
    suspend fun <T> required(work: suspend (TransactionContext) -> T): T

    /** Suspende a transação corrente, se houver, e executa numa transação nova. */
    suspend fun <T> requiresNew(work: suspend (TransactionContext) -> T): T

    /** Exige transação corrente; sem ela, [TransactionRequiredException]. */
    suspend fun <T> mandatory(work: suspend (TransactionContext) -> T): T

    /** Participa da transação corrente se houver; senão executa sem transação. */
    suspend fun <T> supports(work: suspend (TransactionContext) -> T): T

    /** Suspende a transação corrente, se houver, e executa sem transação. */
    suspend fun <T> notSupported(work: suspend (TransactionContext) -> T): T

    /** Exige ausência de transação; com ela, [TransactionNotAllowedException]. */
    suspend fun <T> never(work: suspend (TransactionContext) -> T): T

    /**
     * Agenda [task] para depois do commit da transação corrente: roda só se ela for confirmada, na ordem do agendamento,
     * já fora dela (a conexão devolvida). Desfeita (exceção ou [TransactionContext.setRollbackOnly]), a tarefa é descartada.
     * Sem transação corrente, roda na hora. A falha de uma tarefa vai ao log e não afeta o resultado da transação nem as
     * tarefas seguintes.
     *
     * A tarefa pertence à transação **física**: dentro de um [required] ou [supports] que participa de outra, ela
     * espera o commit da de fora; dentro de um [requiresNew], roda quando ele comita, mesmo que a de fora seja
     * desfeita depois. Em [notSupported] e [never] não há transação: roda na hora. Ao rodar, a tarefa não está em
     * transação nenhuma; se precisar de uma, abre a sua.
     *
     * [AfterCommitTasks] é o executor comum para as implementações.
     */
    suspend fun afterCommit(task: suspend () -> Unit)
}
