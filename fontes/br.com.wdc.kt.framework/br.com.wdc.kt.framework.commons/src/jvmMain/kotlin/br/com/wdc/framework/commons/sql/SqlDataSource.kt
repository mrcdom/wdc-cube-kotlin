package br.com.wdc.framework.commons.sql

import br.com.wdc.framework.commons.util.AtomicRef
import javax.sql.DataSource

interface SqlDataSource : DataSource {
    companion object {
        @Deprecated(
            "Global infrastructure holder. Each module gets its own DataSource from the composition root " +
                "(see TransactionServiceImpl and TransactionAwareConnectionProvider, in framework-persistence/framework-jooq)."
        )
        val BEAN = AtomicRef<SqlDataSource>()
    }
}
