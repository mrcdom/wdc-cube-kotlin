package br.com.wdc.shopping.persistence.repository

import br.com.wdc.shopping.persistence.sql.SqlKeywords
import br.com.wdc.shopping.persistence.sql.SqlList

abstract class BaseApplyCriteria(
    private val cmd: BaseCommand,
) : SqlKeywords {

    protected fun param(name: String, value: Any?): String = cmd.param(name, value)

    abstract fun apply(sql: SqlList)
}
