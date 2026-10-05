package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.domain.transaction.TransactionContext
import br.com.wdc.framework.domain.transaction.TransactionNotAllowedException
import br.com.wdc.framework.domain.transaction.TransactionRequiredException
import br.com.wdc.framework.domain.transaction.TransactionService
import br.com.wdc.framework.domain.transaction.TransactionSystemException
import javax.sql.DataSource
import kotlinx.coroutines.withContext

/**
 * Implementação de [TransactionService] no estilo CMT, sobre JDBC, reentrante.
 *
 * Apoia-se no frame [TransactionScope]: REQUIRED junta-se à transação ativa compartilhando a mesma conexão;
 * REQUIRES_NEW abre uma segunda conexão e a corrente fica intocada até o bloco terminar; NOT_SUPPORTED
 * executa sem transação e a corrente volta ao final.
 *
 * Semântica do `work`: COMMIT em retorno normal (apenas o owner comita), ROLLBACK em **qualquer** exceção
 * (repropagada intacta — inclusive `Error` e cancelamento) ou se [TransactionContext.setRollbackOnly] foi
 * chamado. Falhas ao abrir ou finalizar a transação viram [TransactionSystemException].
 *
 * **Corrotinas.** O `work` roda com o escopo no contexto da corrotina ([TransactionScope.asContextElement]):
 * se suspender e retomar em outra thread, a transação corrente vai junto, com a mesma conexão. Ainda assim,
 * **o bloco não deve fazer suspensão real** (`delay`, espera de rede): a transação segura uma conexão do
 * pool durante a espera. As implementações `suspend` dos repositórios chamam JDBC bloqueante e respeitam isso.
 *
 * @param dataSource DataSource deste contexto (por módulo), resolvido a cada abertura — o composition root o
 *                   injeta no bootstrap do módulo
 */
class TransactionServiceImpl(private val dataSource: () -> DataSource?) : TransactionService {

    override suspend fun <T> required(work: suspend (TransactionContext) -> T): T {
        val ambient = TransactionScope.current()
        return if (ambient != null) join(ambient, work) else runNew("Falha ao abrir/juntar transação", work)
    }

    override suspend fun <T> requiresNew(work: suspend (TransactionContext) -> T): T =
        runNew("Falha ao abrir nova transação", work)

    override suspend fun <T> mandatory(work: suspend (TransactionContext) -> T): T {
        val ambient = TransactionScope.current()
            ?: throw TransactionRequiredException("mandatory: nenhuma transação ativa no escopo")
        return join(ambient, work)
    }

    override suspend fun <T> supports(work: suspend (TransactionContext) -> T): T {
        val ambient = TransactionScope.current()
        return if (ambient != null) join(ambient, work) else work(HANDLE)
    }

    override suspend fun <T> notSupported(work: suspend (TransactionContext) -> T): T =
        withTransaction(null) { work(HANDLE) }

    override suspend fun <T> never(work: suspend (TransactionContext) -> T): T {
        if (TransactionScope.isActive()) {
            throw TransactionNotAllowedException("never: há transação ativa no escopo")
        }
        return work(HANDLE)
    }

    // :: Núcleo

    /** Abre uma transação owner e executa o `work` nela; a transação ambiente, se houver, volta ao final. */
    private suspend fun <T> runNew(openFailure: String, work: suspend (TransactionContext) -> T): T {
        val ds = dataSource() ?: throw TransactionSystemException("DataSource não inicializado para este TransactionService")
        val scope = try {
            TransactionScope.open(ds)
        } catch (e: Exception) {
            throw TransactionSystemException(openFailure, e)
        }
        return withTransaction(scope) { finish(scope, work) }
    }

    /** Executa o `work` como participante da transação ambiente. */
    private suspend fun <T> join(ambient: TransactionScope, work: suspend (TransactionContext) -> T): T =
        // o elemento garante a transação ambiente também quando ela foi ligada direto na thread (coordenador remoto)
        withTransaction(ambient) { finish(ambient.participant(), work) }

    /**
     * Executa o bloco com [scope] como transação corrente da corrotina (`null` = sem transação).
     *
     * A falha do bloco é capturada **dentro** do `withContext` e relançada fora: assim a exceção chega ao
     * chamador como a mesma instância. Atravessando a fronteira como exceção, o modo de depuração das
     * corrotinas (ligado por `-ea`) a substituiria por uma cópia, para recompor a pilha.
     */
    private suspend fun <T> withTransaction(scope: TransactionScope?, block: suspend () -> T): T {
        var failure: Throwable? = null
        val result = withContext(TransactionScope.asContextElement(scope)) {
            try {
                block()
            } catch (e: Throwable) {
                failure = e
                null
            }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /**
     * Executa o `work` no frame: comita em sucesso (só o owner comita; participante é no-op), marca
     * rollback em exceção (repropagada) e encerra o frame.
     */
    private suspend fun <T> finish(scope: TransactionScope, work: suspend (TransactionContext) -> T): T {
        val result = try {
            work(HANDLE)
        } catch (e: Throwable) {
            scope.setRollbackOnly()
            try {
                scope.close()
            } catch (suppressed: Exception) {
                e.addSuppressed(suppressed)
            }
            throw e
        }
        try {
            scope.close()
        } catch (e: Exception) {
            throw TransactionSystemException("Falha ao finalizar a transação", e)
        }
        return result
    }

    /** Handle entregue ao `work`; lê o estado da transação ambiente. */
    private object HANDLE : TransactionContext {

        override fun setRollbackOnly() {
            val current = TransactionScope.current()
                ?: throw IllegalStateException("setRollbackOnly: nenhuma transação ativa neste escopo")
            current.setRollbackOnly()
        }

        override fun isRollbackOnly(): Boolean = TransactionScope.current()?.isRollbackOnly() == true

        override fun isActive(): Boolean = TransactionScope.isActive()
    }
}
