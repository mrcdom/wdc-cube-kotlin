package com.example.tasks.persistence

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.jooq.TransactionAwareConnectionProvider
import br.com.wdc.framework.persistence.transaction.TransactionServiceImpl
import com.example.tasks.data.TaskCriteria
import com.example.tasks.data.TaskEntity
import com.example.tasks.data.TaskRepository
import com.example.tasks.data.TaskTransactions
import io.agroal.api.AgroalDataSource
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier
import io.agroal.api.security.NamePrincipal
import io.agroal.api.security.SimplePassword
import java.time.Duration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * The database the tasks live in, and what reaches it: a pool of connections (Agroal), the `DSLContext` the repository
 * writes its statements with ([DSL_CONTEXT]), the transaction service the write calls run in ([TaskTransactions]) and the
 * repository ([TaskRepository]). [open] registers all of them; closing what it returns takes them back and closes the
 * pool.
 *
 * The database is H2. [MEMORY_URL] (the default) starts empty on every run and is gone when the backend stops; a file
 * keeps the tasks between runs (`TASKS_DB_URL=jdbc:h2:file:./data/tasks`). Another database is another URL and
 * driver, and the `SQLDialect` that goes with it.
 */
object TaskDatabase {

    /** An H2 database in memory, kept while the backend runs (not only while a connection is open). */
    const val MEMORY_URL = "jdbc:h2:mem:tasks;DB_CLOSE_DELAY=-1"

    /** The `DSLContext` on the pool: each statement takes the current transaction's connection, if there is one. */
    val DSL_CONTEXT = AtomicRef<DSLContext>()

    fun open(url: String = MEMORY_URL): AutoCloseable {
        val dataSource = pool(url)
        try {
            dataSource.connection.use(TaskSchema::create)
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }

        DSL_CONTEXT.set(DSL.using(TransactionAwareConnectionProvider(dataSource), SQLDialect.H2))
        TaskTransactions.BEAN.set(TransactionServiceImpl { dataSource })
        TaskRepository.BEAN.set(TaskRepositoryImpl())

        return AutoCloseable {
            TaskRepository.BEAN.set(null)
            TaskTransactions.BEAN.set(null)
            DSL_CONTEXT.set(null)
            dataSource.close()
        }
    }

    /** The first tasks, written only into a database without any (in one transaction: all or none). */
    suspend fun seedIfEmpty(titles: List<String>) {
        val taskRepo = TaskRepository.BEAN.get()
        TaskTransactions.BEAN.get().required {
            if (taskRepo.count(TaskCriteria()) > 0) return@required
            for (seedTitle in titles) {
                taskRepo.insert(
                    TaskEntity().apply {
                        title = seedTitle
                        done = false
                    },
                )
            }
        }
    }

    private fun pool(url: String): AgroalDataSource {
        val factory = AgroalConnectionFactoryConfigurationSupplier()
            .connectionProviderClass(org.h2.jdbcx.JdbcDataSource::class.java)
            .jdbcUrl(url)
            .principal(NamePrincipal("sa"))
            .credential(SimplePassword(""))
        val pool = AgroalConnectionPoolConfigurationSupplier()
            .initialSize(1)
            .minSize(1)
            .maxSize(10)
            .acquisitionTimeout(Duration.ofSeconds(30))
            .connectionFactoryConfiguration(factory)
        return AgroalDataSource.from(AgroalDataSourceConfigurationSupplier().connectionPoolConfiguration(pool))
    }
}
