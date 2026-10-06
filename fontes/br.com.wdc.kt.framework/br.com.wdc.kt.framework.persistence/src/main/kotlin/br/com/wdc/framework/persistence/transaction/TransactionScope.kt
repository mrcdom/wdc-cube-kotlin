package br.com.wdc.framework.persistence.transaction

import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asContextElement

/**
 * Frame de transação JDBC ligado à thread (via [ThreadLocal]).
 *
 * **Reentrância (propagação REQUIRED):** [begin] junta-se à transação ativa quando há uma —
 * **compartilhando a mesma conexão do owner** (uma única transação física) — ou abre uma nova e torna-se
 * owner. Apenas o owner comita/reverte e fecha a conexão no [close]; para participantes o [close] é no-op.
 *
 * **Suspensão (REQUIRES_NEW / NOT_SUPPORTED):** [suspend] desliga a transação corrente da thread (a
 * conexão/transação fica viva, sem dono na thread) e [resume] religa. Um novo escopo aberto nesse intervalo
 * usa uma **segunda conexão** do pool, independente da suspensa.
 *
 * Usado pelo [TransactionServiceImpl] (estilo CMT), pelo coordenador de transação remota e pelos
 * repositórios, que obtêm a conexão da transação corrente via [current].
 *
 * **Corrotinas:** o escopo vive em `ThreadLocal` porque quem o consulta é código bloqueante (o provedor de
 * conexão do jOOQ), que não recebe contexto. Para um bloco `suspend`, [asContextElement] leva o escopo junto
 * com a corrotina: a cada retomada, em qualquer thread, o `ThreadLocal` é reinstalado.
 *
 * **Só JDBC.** A forma já separa os três pontos que um modo JTA precisaria tocar, sem mudar o contrato: a
 * decisão de como abrir fica em [open]; commit/rollback, no [close] do owner; e ligar/desligar da thread,
 * em [suspend]/[resume] (com JTA, a transação passa a ter afinidade de thread, e o bloco transacional deixa
 * de poder trocar de thread).
 */
class TransactionScope private constructor(
    private val connection: Connection,
    /** Escopo que detém a transação física; `this` quando owner. */
    owner: TransactionScope?,
    /** `autoCommit` original, a restaurar no [close] do owner. */
    private val oldAutoCommit: Boolean,
) : AutoCloseable {

    private val root: TransactionScope = owner ?: this

    private val isOwner: Boolean get() = root === this

    @Volatile
    private var rollbackOnly = false

    @Volatile
    private var closed = false

    /**
     * A conexão da transação.
     *
     * @throws IllegalStateException se o escopo já foi fechado
     */
    fun connection(): Connection {
        check(!closed) { "TransactionScope already closed" }
        return connection
    }

    fun isRollbackOnly(): Boolean = root.rollbackOnly

    /** Marca a transação para rollback (sem volta). O commit não será executado no [close] do owner. */
    fun setRollbackOnly() {
        root.rollbackOnly = true
    }

    /**
     * Encerra o frame. Participante: no-op (a conexão pertence ao owner). Owner: comita (ou reverte, se
     * marcado), restaura o `autoCommit` e fecha a conexão.
     */
    @Throws(SQLException::class)
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        if (!isOwner) {
            return
        }
        if (CURRENT.get() === this) {
            CURRENT.remove()
        }
        try {
            if (rollbackOnly) connection.rollback() else connection.commit()
        } catch (ex: SQLException) {
            try {
                connection.rollback()
            } catch (suppressed: SQLException) {
                ex.addSuppressed(suppressed)
            }
            throw ex
        } finally {
            try {
                connection.autoCommit = oldAutoCommit
            } catch (_: SQLException) {
                // melhor esforço
            }
            try {
                connection.close()
            } catch (_: Exception) {
                // melhor esforço
            }
        }
    }

    companion object {
        private val CURRENT = ThreadLocal<TransactionScope?>()

        // :: Propagação REQUIRED

        /**
         * Junta-se à transação ativa (participante que compartilha a conexão do owner) ou abre uma nova
         * (owner, instalada como corrente).
         */
        @Throws(SQLException::class)
        fun begin(dataSource: DataSource): TransactionScope {
            return current()?.participant() ?: beginNew(dataSource)
        }

        /**
         * Abre incondicionalmente uma nova transação owner e a instala como corrente. Deve ser precedido de
         * [suspend] se já houver transação ativa (caso REQUIRES_NEW).
         */
        @Throws(SQLException::class)
        fun beginNew(dataSource: DataSource): TransactionScope {
            val scope = open(dataSource)
            CURRENT.set(scope)
            return scope
        }

        /**
         * Abre uma nova transação owner **sem** instalá-la na thread. Ponto único de decisão de como a
         * transação física nasce.
         */
        @Throws(SQLException::class)
        internal fun open(dataSource: DataSource): TransactionScope {
            val connection = dataSource.connection
            try {
                val oldAutoCommit = connection.autoCommit
                connection.autoCommit = false
                return TransactionScope(connection, null, oldAutoCommit)
            } catch (e: SQLException) {
                try {
                    connection.close()
                } catch (suppressed: Exception) {
                    e.addSuppressed(suppressed)
                }
                throw e
            }
        }

        // :: Suspensão / retomada (REQUIRES_NEW, NOT_SUPPORTED, transação remota)

        /**
         * Desliga a transação corrente da thread e a devolve para posterior [resume]. `null` se não houver
         * transação ativa. A transação suspensa permanece viva (não é comitada).
         */
        fun suspend(): TransactionScope? {
            val current = current()
            CURRENT.remove()
            return current
        }

        /** Religa uma transação previamente suspensa por [suspend]. No-op se [suspended] for `null`. */
        fun resume(suspended: TransactionScope?) {
            if (suspended != null) {
                CURRENT.set(suspended)
            }
        }

        // :: Acesso / estado

        /** Escopo corrente da thread, ou `null` se não houver transação ativa. */
        fun current(): TransactionScope? = CURRENT.get()?.takeUnless { it.closed }

        /** Verifica se há uma transação ativa na thread atual. */
        fun isActive(): Boolean = current() != null

        /**
         * Elemento de contexto que mantém [scope] como transação corrente da corrotina, em qualquer thread em
         * que ela for retomada, e devolve à thread o valor anterior ao sair. `null` executa **sem** transação,
         * mesmo que o contexto externo carregue uma.
         */
        internal fun asContextElement(scope: TransactionScope?): CoroutineContext.Element = CURRENT.asContextElement(scope)
    }

    /** Participante desta transação: compartilha a conexão, sem ciclo de vida próprio. */
    internal fun participant(): TransactionScope = TransactionScope(connection, root, oldAutoCommit)
}
