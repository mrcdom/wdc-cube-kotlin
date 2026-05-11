package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class CountUsersCmd : BaseCommand() {

    companion object {
        fun byCriteria(connection: Connection, criteria: UserCriteria): Int =
            CountUsersCmd().execute(connection, criteria)
    }

    fun execute(connection: Connection, criteria: UserCriteria): Int {
        val en = EnUser("u")

        val sql = SqlList()
        sql.ln(SELECT, COUNT("*"))
        sql.ln(FROM, en.tableRef())
        sql.ln(WHERE_TRUE)

        val applier = ApplyUserCriteria(this)
        applier.criteria = criteria
        applier.root = en
        applier.apply(sql)

        Jdbi.create(connection).open().use { handle ->
            val query = handle.createQuery(sql.toText())
            applyParams(query)
            return query.mapTo(Int::class.javaObjectType).one()
        }
    }
}
