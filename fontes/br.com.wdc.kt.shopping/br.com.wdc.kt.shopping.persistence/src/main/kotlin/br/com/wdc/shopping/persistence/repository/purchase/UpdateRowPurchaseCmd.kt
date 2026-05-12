package br.com.wdc.shopping.persistence.repository.purchase

import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import kotlin.time.toJavaInstant
import org.jdbi.v3.core.Jdbi
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset

class UpdateRowPurchaseCmd : BaseCommand() {

    companion object {
        fun run(connection: Connection, bean: Purchase): Boolean {
            requireNotNull(bean.id) { "Missing primary key" }
            return UpdateRowPurchaseCmd().execute(connection, rowFromBean(bean)) > 0
        }

        fun run(connection: Connection, newBean: Purchase, oldBean: Purchase): Boolean {
            requireNotNull(newBean.id) { "Missing primary key in newUser" }
            requireNotNull(oldBean.id) { "Missing primary key in oldUser" }
            require(newBean.id == oldBean.id) { "New and old bean must have some key value" }

            val row = rowFromBean(oldBean)
            row.clearChanges()

            var hasChanges = false

            val userId = newBean.user?.id
            if (row.userId != userId) {
                row.userId(userId)
                hasChanges = true
            }

            val newBuyDate = newBean.buyDate?.let { OffsetDateTime.ofInstant(it.toJavaInstant(), ZoneOffset.UTC) }
            if (row.buyDate != newBuyDate) {
                row.buyDate(newBuyDate)
                hasChanges = true
            }

            return if (hasChanges) UpdateRowPurchaseCmd().execute(connection, row) > 0 else false
        }

        private fun rowFromBean(bean: Purchase): EnPurchase.Row {
            val row = EnPurchase.Row()
            row.id(bean.id)
            bean.user?.let { row.userId(it.id) }
            row.buyDate(bean.buyDate?.let { OffsetDateTime.ofInstant(it.toJavaInstant(), ZoneOffset.UTC) })
            return row
        }
    }

    fun execute(connection: Connection, row: EnPurchase.Row): Int {
        val en = EnPurchase.INSTANCE
        val sql = SqlList()

        sql.ln(UPDATE, en.tableName(), SET)

        val comma = SqlUtils.comma()
        if (row.userIdChanged) {
            sql.ln(comma(), en.userId, EQUAL, param("userId", row.userId))
        }

        if (row.buyDateChanged) {
            sql.ln(comma(), en.buyDate, EQUAL, param("buyDate", row.buyDate))
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
