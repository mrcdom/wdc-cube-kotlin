package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class InsertRowUserCmd : BaseCommand() {

    companion object {
        fun run(connection: Connection, bean: User): Boolean {
            val row = EnUser.Row()
            row.id(bean.id)

            bean.userName?.let { row.userName(it) }
            bean.password?.let { row.password(it) }
            bean.name?.let { row.name(it) }
            bean.roles?.let { row.roles(it) }

            val inserted = InsertRowUserCmd().execute(connection, row) > 0
            bean.id = row.id
            return inserted
        }
    }

    fun execute(connection: Connection, row: EnUser.Row): Int {
        val en = EnUser.INSTANCE

        if (row.id == null) {
            row.id(en.nextSeqUser(connection))
        }

        val sql = SqlList()
        val places = mutableListOf<String>()

        sql.ln(INSERT_INTO, en.tableName(), '(')
        sql.ln(' ', en.id)
        places.add(param("id", row.id))

        if (row.userNameChanged) {
            sql.ln(',', en.userName)
            places.add(param("userName", row.userName))
        }

        if (row.passwordChanged) {
            sql.ln(',', en.password)
            places.add(param("password", row.password))
        }

        if (row.nameChanged) {
            sql.ln(',', en.name)
            places.add(param("name", row.name))
        }

        if (row.rolesChanged) {
            sql.ln(',', en.roles)
            places.add(param("roles", row.roles))
        }

        sql.add(")")

        sql.add(VALUES)
        sql.add("(${places.joinToString(",")})")

        Jdbi.create(connection).open().use { handle ->
            val update = handle.createUpdate(sql.toText())
            applyParams(update)
            return update.execute()
        }
    }
}
