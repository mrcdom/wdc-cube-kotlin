package br.com.wdc.shopping.persistence.schema

import br.com.wdc.framework.commons.gson.JsonCoerceUtils
import br.com.wdc.framework.commons.gson.JsonReaderHelper
import br.com.wdc.shopping.persistence.schema.support.BaseRow
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.schema.support.DbTable
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import java.math.BigDecimal
import java.sql.Connection

class EnProduct(alias: String) : DbTable(alias) {

    val id = mkBigint("ID", false)
    val name = mkVarCharIgnoreCase("NAME", 1000000, false)
    val price = mkNumeric("PRICE", 20, 2, false)
    val description = mkVarChar("DESCRIPTION", 1000000, false)
    val image = mkBinary("IMAGE", 1000000, true)

    private val _fields = listOf(id, name, price, description, image)

    override fun tableName() = "EN_PRODUCT"
    override fun fields() = _fields

    override fun createTableSql(): String = buildString {
        val baseName = tableName().substring(3)
        appendLine("CREATE TABLE IF NOT EXISTS ${tableName()} (")
        appendLine(" ${id.declaration}")
        appendLine(",${name.declaration}")
        appendLine(",${price.declaration}")
        appendLine(",${description.declaration}")
        appendLine(",${image.declaration}")
        appendLine(",CONSTRAINT PK_$baseName PRIMARY KEY (${id.name})")
        appendLine(")")
    }

    override fun createSequenceSql() = "CREATE SEQUENCE IF NOT EXISTS SQ_PRODUCT START WITH 1 INCREMENT BY 1"

    fun nextSeqProduct(connection: Connection): Long = SqlUtils.nextSequence(connection, "SQ_PRODUCT")
    fun alterSeqProduct(connection: Connection, value: Long) = SqlUtils.alterSequence(connection, "SQ_PRODUCT", value)

    class Row : BaseRow() {
        var id: Long? = null; private set
        var idChanged = false; private set
        fun id(value: Long?): Row { id = value; idChanged = true; return this }

        var name: String? = null; private set
        var nameChanged = false; private set
        fun name(value: String?): Row { name = value; nameChanged = true; return this }

        var price: BigDecimal? = null; private set
        var priceChanged = false; private set
        fun price(value: BigDecimal?): Row { price = value; priceChanged = true; return this }

        var description: String? = null; private set
        var descriptionChanged = false; private set
        fun description(value: String?): Row { description = value; descriptionChanged = true; return this }

        var image: ByteArray? = null; private set
        var imageChanged = false; private set
        fun image(value: ByteArray?): Row { image = value; imageChanged = true; return this }

        override fun clearChanges() {
            idChanged = false; nameChanged = false; priceChanged = false
            descriptionChanged = false; imageChanged = false
        }

        companion object {
            fun parseJson(reader: JsonReader): Row {
                val row = Row()
                val en = INSTANCE
                JsonReaderHelper(reader).`object` { obj0 ->
                    obj0[en.id.name] = { row.id(JsonCoerceUtils.asLong(reader)) }
                    obj0[en.name.name] = { row.name(JsonCoerceUtils.asString(reader)) }
                    obj0[en.price.name] = { row.price(JsonCoerceUtils.asBigDecimal(reader)) }
                    obj0[en.description.name] = { row.description(JsonCoerceUtils.asString(reader)) }
                    obj0[en.image.name] = { row.image(JsonCoerceUtils.asByteArrayFromHex(reader)) }
                }
                return row
            }
        }
    }

    companion object {
        val INSTANCE = EnProduct("")
    }
}
