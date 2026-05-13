package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class DeleteUsersCmd : BaseCommand() {

    companion object {
        fun byId(connection: Connection, userId: Long): Int {
            requireNotNull(userId) { "userId is required" }
            return DeleteUsersCmd().execute(connection, UserCriteria().withUserId(userId))
        }

        fun byCriteria(connection: Connection, criteria: UserCriteria): Int =
            DeleteUsersCmd().execute(connection, criteria)
    }

    fun execute(connection: Connection, criteria: UserCriteria): Int {
        val en = EnUser.INSTANCE

        val sql = SqlList()
        sql.ln(DELETE)
        sql.ln(FROM, en.tableName())
        sql.ln(WHERE_TRUE)

        val applier = ApplyUserCriteria(this)
        applier.criteria = criteria
        applier.root = en
        applier.apply(sql)

        Jdbi.create(connection).open().use { handle ->
            val update = handle.createUpdate(sql.toText())
            applyParams(update)
            return update.execute()
        }
    }
}
