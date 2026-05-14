package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import org.jdbi.v3.core.Jdbi
import java.io.StringReader
import java.sql.Connection

class FetchProductsCmd : BaseCommand() {

    companion object {
        fun byId(connection: Connection, productId: Long, projection: Product?): Product? {
            val list = byCriteria(connection, ProductCriteria()
                .withProductId(productId)
                .withProjection(projection))
            return list.firstOrNull()
        }

        fun byCriteria(connection: Connection, criteria: ProductCriteria): List<Product> =
            FetchProductsCmd().execute(connection, criteria)

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

    fun execute(connection: Connection, criteria: ProductCriteria): List<Product> {
        val sql = SqlList()

        val cteProduct = EnProduct("cteProduct")
        sql.ln(WITH, cteProduct.alias, AS, '(')
        sql.ln(cteProduct(criteria, criteria.projection, null, null).toText("  "))
        sql.ln(')')
        sql.ln(SELECT)

        val fieldsList = fields(criteria.projection, cteProduct)
        val fJsonData = sql.strColumn(SqlUtils.toJsonField(fieldsList), AS, "json_data")
        sql.ln(FROM, cteProduct.alias)

        Jdbi.create(connection).open().use { handle ->
            val query = handle.createQuery(sql.toText())
            applyParams(query)

            val productMap = mutableMapOf<Long, Product>()
            return query.map { rs, _ -> fromJson(fJsonData(rs)!!, productMap) }.list()
        }
    }

    fun cteProduct(criteria: ProductCriteria?, prj: Product?, superAlias: String?, superId: DbField?): SqlList {
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

        if (criteria == null) return sql

        val applier = ApplyProductCriteria(this)
        applier.criteria = criteria
        applier.root = p
        applier.apply(sql)

        criteria.orderBy?.let {
            when (it) {
                ProductCriteria.OrderBy.ASCENDING -> sql.ln(ORDER_BY(p.id.asc()))
                ProductCriteria.OrderBy.DESCENDING -> sql.ln(ORDER_BY(p.id.desc()))
            }
        }

        criteria.limit?.let { sql.ln(LIMIT, it) }
        criteria.offset?.let { sql.ln(OFFSET, it) }

        return sql
    }
}
