package br.com.wdc.framework.jooq

import br.com.wdc.framework.persistence.transaction.TransactionScope
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import org.jooq.ConnectionProvider
import org.jooq.exception.DataAccessException

/**
 * [ConnectionProvider] do jOOQ ciente do [TransactionScope] — é a peça que faz os repositórios participarem
 * da transação sem saber dela.
 *
 * - **Dentro de uma transação** ([TransactionScope.current] não nulo): entrega a conexão do escopo corrente,
 *   de modo que todas as consultas do bloco compartilhem a mesma transação física. Não fecha a conexão no
 *   [release] — o escopo é o dono.
 * - **Fora de transação**: empresta uma conexão avulsa do [DataSource] (autocommit) e a devolve no [release].
 */
class TransactionAwareConnectionProvider(private val dataSource: DataSource) : ConnectionProvider {

    override fun acquire(): Connection {
        TransactionScope.current()?.let { return it.connection() }
        try {
            return dataSource.connection
        } catch (e: SQLException) {
            throw DataAccessException("Não foi possível obter conexão do DataSource", e)
        }
    }

    override fun release(connection: Connection) {
        // acquire()/release() envolvem uma única execução na mesma thread; a conexão do escopo é reconhecida por
        // identidade, então uma avulsa emprestada antes de a transação começar ainda é devolvida.
        if (TransactionScope.current()?.connection() === connection) {
            return
        }
        try {
            connection.close()
        } catch (e: SQLException) {
            throw DataAccessException("Falha ao liberar conexão avulsa", e)
        }
    }
}
