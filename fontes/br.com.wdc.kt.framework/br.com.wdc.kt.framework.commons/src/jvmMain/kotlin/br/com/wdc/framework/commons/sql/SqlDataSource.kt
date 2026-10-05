package br.com.wdc.framework.commons.sql

import br.com.wdc.framework.commons.util.AtomicRef
import javax.sql.DataSource

interface SqlDataSource : DataSource {
    companion object {
        @Deprecated(
            "Holder global de infraestrutura. Cada módulo recebe o seu DataSource do composition root " +
                "(ver TransactionServiceImpl e TransactionAwareConnectionProvider, em framework-persistence/framework-jooq)."
        )
        val BEAN = AtomicRef<SqlDataSource>()
    }
}
