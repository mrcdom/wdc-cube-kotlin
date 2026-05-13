package br.com.wdc.shopping.persistence.repository

import br.com.wdc.shopping.persistence.sql.SqlKeywords
import org.jdbi.v3.core.statement.SqlStatement

open class BaseCommand : SqlKeywords {

    protected var paramsList: MutableList<Pair<String, Any?>>? = null

    internal fun param(name: String, value: Any?): String {
        val list = paramsList ?: mutableListOf<Pair<String, Any?>>().also { paramsList = it }
        list.add(Pair(name, value))
        return ":$name"
    }

    protected fun applyParams(stmt: SqlStatement<*>) {
        paramsList?.forEach { (name, value) ->
            stmt.bind(name, value)
        }
    }

    protected fun paramsIsEmpty(): Boolean = paramsList.isNullOrEmpty()

    internal fun transferParamsTo(target: BaseCommand) {
        paramsList?.let { list ->
            val targetList = target.paramsList ?: mutableListOf<Pair<String, Any?>>().also { target.paramsList = it }
            targetList.addAll(list)
        }
    }
}
