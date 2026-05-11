package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.sql.Connection

class DeleteProductsCmd : BaseCommand() {

    companion object {
        fun byId(connection: Connection, productId: Long): Int {
            return DeleteProductsCmd().execute(connection, ProductCriteria().withProductId(productId))
        }

        fun byCriteria(connection: Connection, criteria: ProductCriteria): Int =
            DeleteProductsCmd().execute(connection, criteria)
    }

    fun execute(connection: Connection, criteria: ProductCriteria): Int {
        requireNotNull(criteria.productId) { "Missing primary key" }

        val p = EnProduct.INSTANCE

        val sql = SqlList()
        sql.ln(DELETE)
        sql.ln(FROM, p.tableName())
        sql.ln(WHERE_TRUE)

        val applier = ApplyProductCriteria(this)
        applier.criteria = criteria
        applier.root = p
        applier.apply(sql)

        Jdbi.create(connection).open().use { handle ->
            val update = handle.createUpdate(sql.toText())
            applyParams(update)
            return update.execute()
        }
    }
}
