package br.com.wdc.shopping.persistence.schema

import br.com.wdc.framework.commons.gson.JsonCoerceUtils
import br.com.wdc.framework.commons.gson.JsonReaderHelper
import br.com.wdc.shopping.persistence.schema.support.BaseRow
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.schema.support.DbTable
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import java.io.PrintWriter
import java.sql.Connection

class EnUser(alias: String) : DbTable(alias) {

    val id = mkBigint("ID", false)
    val userName = mkVarChar("USERNAME", 255, false)
    val password = mkChar("PASSWORD", 32, false)
    val name = mkVarChar("NAME", 255, false)
    val roles = mkVarChar("ROLES", 255, true)

    private val _fields = listOf(id, userName, password, name, roles)

    override fun tableName() = "EN_USER"
    override fun fields() = _fields

    override fun createTableSql(): String = buildString {
        val baseName = tableName().substring(3)
        appendLine("CREATE TABLE IF NOT EXISTS ${tableName()} (")
        appendLine(" ${id.declaration}")
        appendLine(",${userName.declaration}")
        appendLine(",${password.declaration}")
        appendLine(",${name.declaration}")
        appendLine(",${roles.declaration}")
        appendLine(",CONSTRAINT PK_$baseName PRIMARY KEY (${id.name})")
        appendLine(")")
    }

    override fun createSequenceSql() = "CREATE SEQUENCE IF NOT EXISTS SQ_USER START WITH 1 INCREMENT BY 1"

    fun nextSeqUser(connection: Connection): Long = SqlUtils.nextSequence(connection, "SQ_USER")
    fun alterSeqUser(connection: Connection, value: Long) = SqlUtils.alterSequence(connection, "SQ_USER", value)

    class Row : BaseRow() {
        var id: Long? = null; private set
        var idChanged = false; private set
        fun id(value: Long?): Row { id = value; idChanged = true; return this }

        var userName: String? = null; private set
        var userNameChanged = false; private set
        fun userName(value: String?): Row { userName = value; userNameChanged = true; return this }

        var password: String? = null; private set
        var passwordChanged = false; private set
        fun password(value: String?): Row { password = value; passwordChanged = true; return this }

        var name: String? = null; private set
        var nameChanged = false; private set
        fun name(value: String?): Row { name = value; nameChanged = true; return this }

        var roles: String? = null; private set
        var rolesChanged = false; private set
        fun roles(value: String?): Row { roles = value; rolesChanged = true; return this }

        override fun clearChanges() {
            idChanged = false; userNameChanged = false; passwordChanged = false
            nameChanged = false; rolesChanged = false
        }

        companion object {
            fun parseJson(reader: JsonReader): Row {
                val row = Row()
                val en = INSTANCE
                JsonReaderHelper(reader).`object` { obj0 ->
                    obj0[en.id.name] = { row.id(JsonCoerceUtils.asLong(reader)) }
                    obj0[en.userName.name] = { row.userName(JsonCoerceUtils.asString(reader)) }
                    obj0[en.password.name] = { row.password(JsonCoerceUtils.asString(reader)) }
                    obj0[en.name.name] = { row.name(JsonCoerceUtils.asString(reader)) }
                    obj0[en.roles.name] = { row.roles(JsonCoerceUtils.asString(reader)) }
                }
                return row
            }
        }
    }

    companion object {
        val INSTANCE = EnUser("")
    }
}
