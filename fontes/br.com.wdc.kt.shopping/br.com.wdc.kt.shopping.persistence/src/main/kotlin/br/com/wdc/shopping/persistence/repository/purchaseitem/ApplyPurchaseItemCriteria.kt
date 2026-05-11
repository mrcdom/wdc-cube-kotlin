package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.persistence.repository.BaseApplyCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.sql.SqlList

class ApplyPurchaseItemCriteria(cmd: BaseCommand) : BaseApplyCriteria(cmd) {

    lateinit var root: EnPurchaseItem
    lateinit var criteria: PurchaseItemCriteria

    override fun apply(sql: SqlList) {
        criteria.purchaseItemId?.let {
            sql.ln(AND, root.id, EQUAL, param("purchaseItemId", it))
        }

        criteria.purchaseId?.let {
            sql.ln(AND, root.purchaseId, EQUAL, param("purchaseId", it))
        }

        criteria.productId?.let {
            sql.ln(AND, root.productId, EQUAL, param("productId", it))
        }

        criteria.userId?.let {
            sql.ln(AND, EXISTS(buildPurchaseCriteria()))
        }
    }

    private fun buildPurchaseCriteria(): SqlList {
        val p = EnPurchase("P")

        val sql = SqlList()
        sql.ln(SELECT, 1)
        sql.ln(FROM, p.tableRef())
        sql.ln(WHERE, p.id, EQUAL, root.purchaseId)

        criteria.userId?.let {
            sql.ln(AND, p.userId, EQUAL, param("userId", it))
        }

        return sql
    }
}
