package br.com.wdc.framework.commons.sql

import br.com.wdc.framework.commons.util.AtomicRef
import javax.sql.DataSource

interface SqlDataSource : DataSource {
    companion object {
        val BEAN = AtomicRef<SqlDataSource>()
    }
}
