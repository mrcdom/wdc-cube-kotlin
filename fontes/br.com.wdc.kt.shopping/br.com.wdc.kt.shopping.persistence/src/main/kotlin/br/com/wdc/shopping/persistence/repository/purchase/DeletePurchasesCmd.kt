package br.com.wdc.shopping.persistence.repository.purchase

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class DeletePurchasesCmd : BaseCommand() {

    companion object {
        fun byId(connection: Connection, purchaseId: Long): Int {
            return DeletePurchasesCmd().execute(connection, PurchaseCriteria().withPurchaseId(purchaseId))
        }

        fun byCriteria(connection: Connection, criteria: PurchaseCriteria): Int =
            DeletePurchasesCmd().execute(connection, criteria)
    }

    fun execute(connection: Connection, criteria: PurchaseCriteria): Int {
        val en = EnPurchase.INSTANCE

        val sql = SqlList()
        sql.ln(DELETE)
        sql.ln(FROM, en.tableName())
        sql.ln(WHERE_TRUE)

        val applier = ApplyPurchaseCriteria(this)
        applier.criteria = criteria
        applier.root = en
        applier.apply(sql)

        Jdbi.create(connection).open().use { handle ->
            val update = handle.createUpdate(sql.toText())
            applyParams(update)
            return update.execute()
        }
    }
}
