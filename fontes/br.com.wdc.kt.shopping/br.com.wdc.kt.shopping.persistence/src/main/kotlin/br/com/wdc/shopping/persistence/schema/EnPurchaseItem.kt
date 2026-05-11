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

class EnPurchaseItem(alias: String) : DbTable(alias) {

    val id = mkBigint("ID", false)
    val purchaseId = mkBigint("PURCHASEID", false)
    val productId = mkBigint("PRODUCTID", false)
    val amount = mkInt("AMOUNT", false)
    val price = mkNumeric("PRICE", 20, 2, false)

    private val _fields = listOf(id, purchaseId, productId, amount, price)

    override fun tableName() = "EN_PURCHASEITEM"
    override fun fields() = _fields

    override fun createTableSql(): String = buildString {
        val baseName = tableName().substring(3)
        val enProduct = EnProduct.INSTANCE
        val enPurchase = EnPurchase.INSTANCE
        val ident = "                             "

        appendLine("CREATE TABLE IF NOT EXISTS ${tableName()} (")
        appendLine(" ${id.declaration}")
        appendLine(",${purchaseId.declaration}")
        appendLine(",${productId.declaration}")
        appendLine(",${amount.declaration}")
        appendLine(",${price.declaration}")
        appendLine(",CONSTRAINT PK_$baseName PRIMARY KEY (${id.name})")
        appendLine(",CONSTRAINT FK_${baseName}_PRODUCT FOREIGN KEY (${productId.name})")
        appendLine("${ident}REFERENCES ${enProduct.tableName()}(${enProduct.id.name})")
        appendLine(",CONSTRAINT FK_${baseName}_PURCHASE FOREIGN KEY (${purchaseId.name})")
        appendLine("${ident}REFERENCES ${enPurchase.tableName()}(${enPurchase.id.name})")
        appendLine(")")
    }

    override fun createSequenceSql() = "CREATE SEQUENCE IF NOT EXISTS SQ_PURCHASEITEM START WITH 1 INCREMENT BY 1"

    fun nextSeqPurchaseItem(connection: Connection): Long = SqlUtils.nextSequence(connection, "SQ_PURCHASEITEM")
    fun alterSeqPurchaseItem(connection: Connection, value: Long) = SqlUtils.alterSequence(connection, "SQ_PURCHASEITEM", value)

    class Row : BaseRow() {
        var id: Long? = null; private set
        var idChanged = false; private set
        fun id(value: Long?): Row { id = value; idChanged = true; return this }

        var purchaseId: Long? = null; private set
        var purchaseIdChanged = false; private set
        fun purchaseId(value: Long?): Row { purchaseId = value; purchaseIdChanged = true; return this }

        var productId: Long? = null; private set
        var productIdChanged = false; private set
        fun productId(value: Long?): Row { productId = value; productIdChanged = true; return this }

        var amount: Int? = null; private set
        var amountChanged = false; private set
        fun amount(value: Int?): Row { amount = value; amountChanged = true; return this }

        var price: BigDecimal? = null; private set
        var priceChanged = false; private set
        fun price(value: BigDecimal?): Row { price = value; priceChanged = true; return this }

        override fun clearChanges() {
            idChanged = false; purchaseIdChanged = false; productIdChanged = false
            amountChanged = false; priceChanged = false
        }

        companion object {
            fun parseJson(reader: JsonReader): Row {
                val row = Row()
                val en = INSTANCE
                JsonReaderHelper(reader).`object` { obj0 ->
                    obj0[en.id.name] = { row.id(JsonCoerceUtils.asLong(reader)) }
                    obj0[en.amount.name] = { row.amount(JsonCoerceUtils.asInteger(reader)) }
                    obj0[en.price.name] = { row.price(JsonCoerceUtils.asBigDecimal(reader)) }
                    obj0[en.purchaseId.name] = { row.purchaseId(JsonCoerceUtils.asLong(reader)) }
                    obj0[en.productId.name] = { row.productId(JsonCoerceUtils.asLong(reader)) }
                }
                return row
            }
        }
    }

    companion object {
        val INSTANCE = EnPurchaseItem("")
    }
}
