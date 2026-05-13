package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class CountProductsCmd : BaseCommand() {

    companion object {
        fun byCriteria(connection: Connection, criteria: ProductCriteria): Int =
            CountProductsCmd().execute(connection, criteria)
    }

    fun execute(connection: Connection, criteria: ProductCriteria): Int {
        val en = EnProduct.INSTANCE

        val sql = SqlList()
        sql.ln(SELECT, COUNT("*"))
        sql.ln(FROM, en.tableRef())
        sql.ln(WHERE_TRUE)

        val applier = ApplyProductCriteria(this)
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
