package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import org.jdbi.v3.core.Jdbi
import java.math.BigDecimal
import java.sql.Connection

class UpdateRowPurchaseItemCmd : BaseCommand() {

    companion object {
        fun run(connection: Connection, bean: PurchaseItem): Boolean {
            requireNotNull(bean.id) { "Missing primary key" }
            return UpdateRowPurchaseItemCmd().execute(connection, rowFromBean(bean)) > 0
        }

        fun run(connection: Connection, newBean: PurchaseItem, oldBean: PurchaseItem): Boolean {
            requireNotNull(newBean.id) { "Missing primary key in newUser" }
            requireNotNull(oldBean.id) { "Missing primary key in oldUser" }
            require(newBean.id == oldBean.id) { "New and old bean must have some key value" }

            val row = rowFromBean(oldBean)
            row.clearChanges()

            var hasChanges = false

            val purchaseId = newBean.purchase?.id
            if (row.purchaseId != purchaseId) {
                row.purchaseId(purchaseId)
                hasChanges = true
            }

            val productId = newBean.product?.id
            if (row.productId != productId) {
                row.productId(productId)
                hasChanges = true
            }

            if (row.amount != newBean.amount) {
                row.amount(newBean.amount)
                hasChanges = true
            }

            val newPrice = newBean.price?.let { BigDecimal.valueOf(it) }
            if (row.price != newPrice) {
                row.price(newPrice)
                hasChanges = true
            }

            return if (hasChanges) UpdateRowPurchaseItemCmd().execute(connection, row) > 0 else false
        }

        private fun rowFromBean(bean: PurchaseItem): EnPurchaseItem.Row {
            val row = EnPurchaseItem.Row()
            row.id(bean.id)
            bean.purchase?.let { row.purchaseId(it.id) }
            bean.product?.let { row.productId(it.id) }
            row.amount(bean.amount)
            bean.price?.let { row.price(BigDecimal.valueOf(it)) }
            return row
        }
    }

    fun execute(connection: Connection, row: EnPurchaseItem.Row): Int {
        val en = EnPurchaseItem.INSTANCE
        val sql = SqlList()

        sql.ln(UPDATE, en.tableName(), SET)

        val comma = SqlUtils.comma()
        if (row.purchaseIdChanged) sql.ln(comma(), en.purchaseId, EQUAL, param("purchaseId", row.purchaseId))
        if (row.productIdChanged) sql.ln(comma(), en.productId, EQUAL, param("productId", row.productId))
        if (row.amountChanged) sql.ln(comma(), en.amount, EQUAL, param("amount", row.amount))
        if (row.priceChanged) sql.ln(comma(), en.price, EQUAL, param("price", row.price))

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
