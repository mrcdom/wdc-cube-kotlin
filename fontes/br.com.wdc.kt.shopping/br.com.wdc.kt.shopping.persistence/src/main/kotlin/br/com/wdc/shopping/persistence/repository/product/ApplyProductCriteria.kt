package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.persistence.repository.BaseApplyCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.sql.SqlList

class ApplyProductCriteria(cmd: BaseCommand) : BaseApplyCriteria(cmd) {

    lateinit var root: EnProduct
    lateinit var criteria: ProductCriteria

    override fun apply(sql: SqlList) {
        criteria.productId?.let {
            sql.ln(AND, root.id, EQUAL, param("productId", it))
        }
    }
}
