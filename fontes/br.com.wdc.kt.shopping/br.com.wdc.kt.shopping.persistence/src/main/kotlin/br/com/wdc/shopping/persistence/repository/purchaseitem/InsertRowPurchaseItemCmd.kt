package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.math.BigDecimal
import java.sql.Connection

class InsertRowPurchaseItemCmd : BaseCommand() {

    companion object {
        fun run(connection: Connection, bean: PurchaseItem): Boolean {
            val row = EnPurchaseItem.Row()
            row.id(bean.id)

            if (bean.purchase != null && bean.purchase!!.id != null) {
                row.purchaseId(bean.purchase!!.id)
            }

            if (bean.product != null && bean.product!!.id != null) {
                row.productId(bean.product!!.id)
            }

            bean.amount?.let { row.amount(it) }
            bean.price?.let { row.price(BigDecimal.valueOf(it)) }

            val inserted = InsertRowPurchaseItemCmd().execute(connection, row) > 0
            bean.id = row.id
            return inserted
        }
    }

    fun execute(connection: Connection, row: EnPurchaseItem.Row): Int {
        val en = EnPurchaseItem.INSTANCE

        checkConstraints(row)

        if (row.id == null) {
            row.id(en.nextSeqPurchaseItem(connection))
        }

        val sql = SqlList()
        val places = mutableListOf<String>()

        sql.ln(INSERT_INTO, en.tableName(), '(')

        sql.ln(' ', en.id)
        places.add(param("id", row.id))

        if (row.purchaseIdChanged) {
            sql.ln(',', en.purchaseId)
            places.add(param("purchaseId", row.purchaseId))
        }

        if (row.productIdChanged) {
            sql.ln(',', en.productId)
            places.add(param("productId", row.productId))
        }

        if (row.amountChanged) {
            sql.ln(',', en.amount)
            places.add(param("amount", row.amount))
        }

        if (row.priceChanged) {
            sql.ln(',', en.price)
            places.add(param("price", row.price))
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

    private fun checkConstraints(row: EnPurchaseItem.Row) {
        requireNotNull(row.purchaseId) { "purchaseId is required" }
        requireNotNull(row.productId) { "productId is required" }
        requireNotNull(row.amount) { "amount is required" }
        requireNotNull(row.price) { "price is required" }
        require(row.amount!! > 0) { "amount must be greater than ZERO" }
    }
}
