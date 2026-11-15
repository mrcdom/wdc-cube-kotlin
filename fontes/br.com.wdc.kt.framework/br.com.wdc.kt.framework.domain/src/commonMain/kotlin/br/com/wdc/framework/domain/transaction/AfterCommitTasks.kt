package br.com.wdc.framework.domain.transaction

import br.com.wdc.framework.commons.log.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * A fila de tarefas de [TransactionService.afterCommit] de uma transação física, e o jeito comum de rodá-las.
 *
 * As implementações guardam uma fila por transação física, chamam [add] enquanto ela está aberta e, depois do commit
 * (com a conexão devolvida e fora da transação), [run]. Desfeita a transação, basta descartar a fila.
 *
 * Não é segura para uso concorrente, nem precisa: uma transação é usada por uma corrotina por vez, e a passagem
 * de uma thread a outra já é ordenada por quem a faz (o despachante da corrotina, o registro de transações remotas).
 */
class AfterCommitTasks {

    private val tasks = ArrayList<suspend () -> Unit>()

    /** Acrescenta uma tarefa ao fim da fila. */
    fun add(task: suspend () -> Unit) {
        tasks.add(task)
    }

    /** Esvazia a fila e devolve o que havia, na ordem do agendamento. */
    fun drain(): List<suspend () -> Unit> = tasks.toList().also { tasks.clear() }

    /** Esvazia a fila e roda o que havia (ver [runAll]). */
    suspend fun run() = runAll(drain())

    companion object {
        /**
         * Roda as tarefas na ordem. A falha de uma vai ao log e não impede as seguintes: o commit já aconteceu, e
         * relançar faria o chamador achar que a gravação falhou. Cancelamento da corrotina é relançado.
         */
        suspend fun runAll(tasks: List<suspend () -> Unit>) {
            for (task in tasks) {
                try {
                    task()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.getLogger("AfterCommitTasks").error("Falha numa tarefa agendada para depois do commit", e)
                }
            }
        }
    }
}
