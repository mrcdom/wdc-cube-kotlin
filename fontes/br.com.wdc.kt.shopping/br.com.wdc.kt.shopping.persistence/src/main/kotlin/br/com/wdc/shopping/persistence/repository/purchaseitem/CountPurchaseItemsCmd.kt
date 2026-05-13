package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class CountPurchaseItemsCmd : BaseCommand() {

    companion object {
        fun byCriteria(connection: Connection, criteria: PurchaseItemCriteria): Int =
            CountPurchaseItemsCmd().execute(connection, criteria)
    }

    fun execute(connection: Connection, criteria: PurchaseItemCriteria): Int {
        val en = EnPurchaseItem.INSTANCE

        val sql = SqlList()
        sql.ln(SELECT, COUNT("*"))
        sql.ln(FROM, en.tableRef())
        sql.ln(WHERE_TRUE)

        val applier = ApplyPurchaseItemCriteria(this)
        applier.criteria = criteria
        applier.root = en
        applier.apply(sql)

        Jdbi.create(connection).open().use { handle ->
            val query = handle.createQuery(sql.toText())
            applyParams(query)
            return query.mapTo(Int::class.javaObjectType).one()
        }
    }
}
