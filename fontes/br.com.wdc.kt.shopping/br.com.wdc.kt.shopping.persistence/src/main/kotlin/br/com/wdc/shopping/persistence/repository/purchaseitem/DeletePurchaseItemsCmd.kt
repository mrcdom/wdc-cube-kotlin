package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class DeletePurchaseItemsCmd : BaseCommand() {

    companion object {
        fun byId(connection: Connection, purchaseItemId: Long): Int {
            return DeletePurchaseItemsCmd().execute(connection, PurchaseItemCriteria()
                .withPurchaseItemId(purchaseItemId))
        }

        fun byCriteria(connection: Connection, criteria: PurchaseItemCriteria): Int =
            DeletePurchaseItemsCmd().execute(connection, criteria)
    }

    fun execute(connection: Connection, criteria: PurchaseItemCriteria): Int {
        val crit = criteria

        val en = EnPurchaseItem.INSTANCE

        val sql = SqlList()
        sql.ln(DELETE)
        sql.ln(FROM, en.tableName())
        sql.ln(WHERE_TRUE)

        val applier = ApplyPurchaseItemCriteria(this)
        applier.root = en
        applier.criteria = crit
        applier.apply(sql)

        Jdbi.create(connection).open().use { handle ->
            val update = handle.createUpdate(sql.toText())
            applyParams(update)
            return update.execute()
        }
    }
}
