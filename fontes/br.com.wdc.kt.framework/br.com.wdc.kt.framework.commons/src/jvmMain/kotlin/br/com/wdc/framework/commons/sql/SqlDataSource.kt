package br.com.wdc.framework.commons.sql

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

interface SqlDataSource : DataSource {
    companion object {
        @JvmField
        val BEAN = AtomicReference<SqlDataSource>()
    }
}
