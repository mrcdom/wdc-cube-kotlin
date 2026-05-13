package br.com.wdc.shopping.persistence.repository.purchase

import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.repository.purchaseitem.InsertRowPurchaseItemCmd
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.sql.SqlList
import kotlin.time.toJavaInstant
import org.jdbi.v3.core.Jdbi
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset

class InsertRowPurchaseCmd : BaseCommand() {

    companion object {
        fun run(connection: Connection, bean: Purchase): Boolean {
            val row = EnPurchase.Row()
            row.id(bean.id)

            if (bean.user != null && bean.user!!.id != null) {
                row.userId(bean.user!!.id)
            }

            bean.buyDate?.let { row.buyDate(OffsetDateTime.ofInstant(it.toJavaInstant(), ZoneOffset.UTC)) }

            val inserted = InsertRowPurchaseCmd().execute(connection, row) > 0
            bean.id = row.id
            return inserted
        }

        fun runWithItems(connection: Connection, purchase: Purchase): Boolean {
            if (!run(connection, purchase)) return false

            purchase.items?.forEach { item ->
                item.purchase = purchase
                InsertRowPurchaseItemCmd.run(connection, item)
            }

            return true
        }
    }

    fun execute(connection: Connection, row: EnPurchase.Row): Int {
        val en = EnPurchase.INSTANCE

        checkConstraints(row)

        if (row.id == null) {
            row.id(en.nextSeqPurchase(connection))
        }

        val sql = SqlList()
        val places = mutableListOf<String>()

        sql.ln(INSERT_INTO, en.tableName(), '(')

        sql.ln(' ', en.id)
        places.add(param("id", row.id))

        if (row.userIdChanged) {
            sql.ln(',', en.userId)
            places.add(param("userId", row.userId))
        }

        if (row.buyDateChanged) {
            sql.ln(',', en.buyDate)
            places.add(param("buyDate", row.buyDate))
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

    private fun checkConstraints(row: EnPurchase.Row) {
        requireNotNull(row.userId) { "userId is required" }
        requireNotNull(row.buyDate) { "buyDate is required" }
    }
}
