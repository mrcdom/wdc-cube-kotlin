package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import org.jdbi.v3.core.Jdbi
import java.math.BigDecimal
import java.sql.Connection

class UpdateProductRowCmd : BaseCommand() {

    companion object {
        fun run(connection: Connection, product: Product): Boolean {
            requireNotNull(product.id) { "Missing primary key" }
            return UpdateProductRowCmd().execute(connection, rowFromBean(product)) > 0
        }

        fun run(connection: Connection, newBean: Product, oldBean: Product): Boolean {
            requireNotNull(newBean.id) { "Missing primary key in newProd" }
            requireNotNull(oldBean.id) { "Missing primary key in oldProd" }
            require(newBean.id == oldBean.id) { "New and old bean must have some key value" }

            val row = rowFromBean(oldBean)

            var hasChanges = false

            if (row.name != newBean.name) {
                row.name(newBean.name)
                hasChanges = true
            }

            val newPrice = CoerceUtils.asBigDecimal(newBean.price)
            if (row.price != newPrice) {
                row.price(newPrice)
                hasChanges = true
            }

            if (row.description != newBean.description) {
                row.description(newBean.description)
                hasChanges = true
            }

            if (!row.image.contentEquals(newBean.image)) {
                row.image(newBean.image)
                hasChanges = true
            }

            return if (hasChanges) UpdateProductRowCmd().execute(connection, row) > 0 else false
        }

        private fun rowFromBean(bean: Product): EnProduct.Row {
            val row = EnProduct.Row()
            row.id(bean.id)
            row.name(bean.name)
            row.description(bean.description)
            row.image(bean.image)
            row.price(BigDecimal.valueOf(bean.price!!))
            return row
        }
    }

    fun execute(connection: Connection, row: EnProduct.Row): Int {
        val en = EnProduct.INSTANCE
        val sql = SqlList()

        sql.ln(UPDATE, en.tableName(), SET)

        val comma = SqlUtils.comma()
        if (row.nameChanged) sql.ln(comma(), en.name, EQUAL, param("name", row.name))
        if (row.priceChanged) sql.ln(comma(), en.price, EQUAL, param("price", row.price))
        if (row.descriptionChanged) sql.ln(comma(), en.description, EQUAL, param("description", row.description))
        if (row.imageChanged) sql.ln(comma(), en.image, EQUAL, param("image", row.image))

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
