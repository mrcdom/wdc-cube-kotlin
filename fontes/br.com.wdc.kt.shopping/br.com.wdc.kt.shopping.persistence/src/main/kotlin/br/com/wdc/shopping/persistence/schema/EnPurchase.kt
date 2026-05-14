package br.com.wdc.shopping.persistence.schema

import br.com.wdc.framework.commons.gson.JsonExtensibleObjectInput
import br.com.wdc.framework.commons.gson.JsonReaderHelper
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.asJavaOffsetDateTime
import br.com.wdc.shopping.persistence.schema.support.BaseRow
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.schema.support.DbTable
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import java.sql.Connection
import java.time.OffsetDateTime

class EnPurchase(alias: String) : DbTable(alias) {

    val id = mkBigint("ID", false)
    val userId = mkBigint("USERID", false)
    val buyDate = mkTimestamp("BUYDATE", false)

    private val _fields = listOf(id, userId, buyDate)

    override fun tableName() = "EN_PURCHASE"
    override fun fields() = _fields

    override fun createTableSql(): String = buildString {
        val baseName = tableName().substring(3)
        val enUser = EnUser.INSTANCE
        appendLine("CREATE TABLE IF NOT EXISTS ${tableName()} (")
        appendLine(" ${id.declaration}")
        appendLine(",${userId.declaration}")
        appendLine(",${buyDate.declaration}")
        appendLine(",CONSTRAINT PK_$baseName PRIMARY KEY (${id.name})")
        appendLine(",CONSTRAINT FK_${baseName}_USER FOREIGN KEY (${userId.name})")
        appendLine("                             REFERENCES ${enUser.tableName()}(${enUser.id.name})")
        appendLine(")")
    }

    override fun createSequenceSql() = "CREATE SEQUENCE IF NOT EXISTS SQ_PURCHASE START WITH 1 INCREMENT BY 1"

    fun nextSeqPurchase(connection: Connection): Long = SqlUtils.nextSequence(connection, "SQ_PURCHASE")
    fun alterSeqPurchase(connection: Connection, value: Long) = SqlUtils.alterSequence(connection, "SQ_PURCHASE", value)

    class Row : BaseRow() {
        var id: Long? = null; private set
        var idChanged = false; private set
        fun id(value: Long?): Row { id = value; idChanged = true; return this }

        var userId: Long? = null; private set
        var userIdChanged = false; private set
        fun userId(value: Long?): Row { userId = value; userIdChanged = true; return this }

        var buyDate: OffsetDateTime? = null; private set
        var buyDateChanged = false; private set
        fun buyDate(value: OffsetDateTime?): Row { buyDate = value; buyDateChanged = true; return this }

        override fun clearChanges() {
            idChanged = false; userIdChanged = false; buyDateChanged = false
        }

        companion object {
            fun parseJson(reader: JsonReader): Row {
                val input = JsonExtensibleObjectInput(reader)
                val row = Row()
                val en = INSTANCE
                JsonReaderHelper(reader).`object` { obj0 ->
                    obj0[en.id.name] = { row.id(InputCoerceUtils.asLong(input)) }
                    obj0[en.buyDate.name] = { row.buyDate(InputCoerceUtils.asJavaOffsetDateTime(input)) }
                    obj0[en.userId.name] = { row.userId(InputCoerceUtils.asLong(input)) }
                }
                return row
            }
        }
    }

    companion object {
        val INSTANCE = EnPurchase("")
    }
}
