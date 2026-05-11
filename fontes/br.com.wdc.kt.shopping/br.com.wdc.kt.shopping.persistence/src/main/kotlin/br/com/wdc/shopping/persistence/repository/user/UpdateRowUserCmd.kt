package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class UpdateRowUserCmd : BaseCommand() {

    companion object {
        fun run(connection: Connection, bean: User): Boolean {
            requireNotNull(bean.id) { "Missing primary key" }
            return UpdateRowUserCmd().execute(connection, rowFromBean(bean)) > 0
        }

        fun run(connection: Connection, newBean: User, oldBean: User): Boolean {
            requireNotNull(newBean.id) { "Missing primary key in newUser" }
            requireNotNull(oldBean.id) { "Missing primary key in oldUser" }
            require(newBean.id == oldBean.id) { "New and old bean must have some key value" }

            val row = rowFromBean(oldBean)
            row.clearChanges()

            var hasChanges = false

            if (row.userName != newBean.userName) {
                row.userName(newBean.userName)
                hasChanges = true
            }
            if (row.password != newBean.password) {
                row.password(newBean.password)
                hasChanges = true
            }
            if (row.name != newBean.name) {
                row.name(newBean.name)
                hasChanges = true
            }
            if (row.roles != newBean.roles) {
                row.roles(newBean.roles)
                hasChanges = true
            }

            return if (hasChanges) UpdateRowUserCmd().execute(connection, row) > 0 else false
        }

        private fun rowFromBean(bean: User): EnUser.Row {
            val row = EnUser.Row()
            row.id(bean.id)
            row.userName(bean.userName)
            row.password(bean.password)
            row.name(bean.name)
            row.roles(bean.roles)
            return row
        }
    }

    fun execute(connection: Connection, row: EnUser.Row): Int {
        val en = EnUser.INSTANCE
        val sql = SqlList()

        sql.ln(UPDATE, en.tableName(), SET)

        val comma = SqlUtils.comma()
        if (row.userNameChanged) {
            sql.ln(comma(), en.userName, EQUAL, param("userName", row.userName))
        }
        if (row.passwordChanged) {
            sql.ln(comma(), en.password, EQUAL, param("password", row.password))
        }
        if (row.nameChanged) {
            sql.ln(comma(), en.name, EQUAL, param("name", row.name))
        }
        if (row.rolesChanged) {
            sql.ln(comma(), en.roles, EQUAL, param("roles", row.roles))
        }

        if (paramsIsEmpty()) return 0

        if (row.id != null) {
            sql.ln(WHERE, en.id, EQUAL, param("id", row.id))
        } else {
            throw AssertionError("Missing primary key")
        }

        Jdbi.create(connection).open().use { handle ->
            val update = handle.createUpdate(sql.toText())
            applyParams(update)
            return update.execute()
        }
    }
}
