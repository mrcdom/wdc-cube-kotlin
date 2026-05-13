package br.com.wdc.shopping.persistence.repository.purchase

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class CountPurchasesCmd : BaseCommand() {

    companion object {
        fun byCriteria(connection: Connection, criteria: PurchaseCriteria): Int =
            CountPurchasesCmd().execute(connection, criteria)
    }

    fun execute(connection: Connection, criteria: PurchaseCriteria): Int {
        paramsList = mutableListOf()

        val pi = EnPurchaseItem("PI")
        val b = EnPurchase("B")

        val sql = SqlList()
        sql.ln(SELECT, COUNT(DISTINCT, b.id))
        sql.ln(FROM, pi.tableRef())
        sql.ln(JOIN, b.tableRef(), ON, pi.purchaseId, EQUAL, b.id)
        sql.ln(WHERE_TRUE)

        val applier = ApplyPurchaseCriteria(this)
        applier.criteria = criteria
        applier.root = b
        applier.apply(sql)

        Jdbi.create(connection).open().use { handle ->
            val query = handle.createQuery(sql.toText())
            applyParams(query)
            return query.mapTo(Int::class.javaObjectType).one()
        }
    }
}
