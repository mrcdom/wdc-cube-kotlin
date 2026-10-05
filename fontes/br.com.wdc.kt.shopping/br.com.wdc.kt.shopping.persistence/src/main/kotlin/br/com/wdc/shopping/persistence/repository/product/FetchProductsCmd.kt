package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import java.io.StringReader

/**
 * O que resta do acesso JDBI a produto: a projeção do produto embutida nas consultas de compra e de item de
 * compra, que ainda não foram portadas para jOOQ. As operações do repositório estão em [ProductRepositoryImpl].
 */
class FetchProductsCmd : BaseCommand() {

    companion object {
        fun fields(prj: Product?, en: EnProduct): List<DbField> {
            val pv = ProjectionValues
            var p = prj
            if (p == null) {
                p = Product()
                p.name = pv.str
                p.price = pv.f64
                p.description = pv.str
            }
            p.id = pv.i64

            val fields = mutableListOf<DbField>()
            if (p.id != null) fields.add(en.id)
            if (p.name != null) fields.add(en.name)
            if (p.price != null) fields.add(en.price)
            if (p.description != null) fields.add(en.description)
            if (p.image != null) fields.add(en.image)
            return fields
        }

        fun fromJson(json: String, productMap: MutableMap<Long, Product>): Product {
            JsonReader(StringReader(json)).use { reader ->
                val row = EnProduct.Row.parseJson(reader)

                val product = productMap.getOrPut(row.id!!) {
                    Product().also { it.id = row.id }
                }

                if (product.name == null) product.name = row.name
                if (product.description == null) product.description = row.description
                if (product.image == null) product.image = row.image
                if (product.price == null) product.price = CoerceUtils.asDouble(row.price)
                return product
            }
        }
    }

    fun cteProduct(prj: Product?, superAlias: String?, superId: DbField?): SqlList {
        val p = EnProduct("P")

        val sql = SqlList()
        sql.ln(SELECT)
        fields(prj, p).forEach { sql.field(it) }
        sql.ln(FROM, p.tableRef())
        sql.ln(WHERE_TRUE)

        if (superAlias != null) {
            sql.ln(AND, EXISTS { ll ->
                ll.ln(SELECT, 1)
                ll.ln(FROM, superAlias)
                ll.ln(WHERE, superId, EQUAL, p.id)
            })
        }

        return sql
    }
}
