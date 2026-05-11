package br.com.wdc.shopping.persistence.repository

import br.com.wdc.framework.commons.sql.SqlDataSource
import br.com.wdc.shopping.domain.exception.BusinessException
import javax.sql.DataSource

open class BaseRepository {

    protected fun dataSource(): DataSource = SqlDataSource.BEAN.get()

    protected fun readException(e: Exception): Nothing {
        throw BusinessException("Error reading data", e)
    }

    protected fun writeException(e: Exception): Nothing {
        throw BusinessException("Error writing data", e)
    }
}
