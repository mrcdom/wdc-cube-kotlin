package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.sql.SqlList
import org.jdbi.v3.core.Jdbi
import java.math.BigDecimal
import java.sql.Connection

class InsertProductRowCmd : BaseCommand() {

    companion object {
        fun run(connection: Connection, bean: Product): Boolean {
            val row = EnProduct.Row()
            row.id(bean.id)
            bean.name?.let { row.name(it) }
            bean.description?.let { row.description(it) }
            bean.image?.let { row.image(it) }
            bean.price?.let { row.price(BigDecimal.valueOf(it)) }

            val inserted = InsertProductRowCmd().execute(connection, row) > 0
            bean.id = row.id
            return inserted
        }
    }

    fun execute(connection: Connection, row: EnProduct.Row): Int {
        val en = EnProduct.INSTANCE

        if (row.id == null) {
            row.id(en.nextSeqProduct(connection))
        }

        val sql = SqlList()
        val places = mutableListOf<String>()

        sql.ln(INSERT_INTO, en.tableName(), '(')
        sql.ln(' ', en.id)
        places.add(param("id", row.id))

        if (row.nameChanged) {
            sql.ln(',', en.name)
            places.add(param("name", row.name))
        }

        if (row.priceChanged) {
            sql.ln(',', en.price)
            places.add(param("price", row.price))
        }

        if (row.descriptionChanged) {
            sql.ln(',', en.description)
            places.add(param("description", row.description))
        }

        if (row.imageChanged) {
            sql.ln(',', en.image)
            places.add(param("image", row.image))
        }

        sql.ln(")")

        sql.ln(VALUES)
        sql.ln("(${places.joinToString(",")})")

        Jdbi.create(connection).open().use { handle ->
            val update = handle.createUpdate(sql.toText())
            applyParams(update)
            return update.execute()
        }
    }
}
