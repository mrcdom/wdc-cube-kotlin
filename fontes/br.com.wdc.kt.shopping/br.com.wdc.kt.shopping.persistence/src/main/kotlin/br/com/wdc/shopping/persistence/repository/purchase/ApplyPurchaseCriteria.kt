package br.com.wdc.shopping.persistence.repository.purchase

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.persistence.repository.BaseApplyCriteria
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.sql.SqlList

class ApplyPurchaseCriteria(cmd: BaseCommand) : BaseApplyCriteria(cmd) {

    lateinit var root: EnPurchase
    lateinit var criteria: PurchaseCriteria

    override fun apply(sql: SqlList) {
        criteria.purchaseId?.let {
            sql.ln(AND, root.id, EQUAL, param("purchaseId", it))
        }

        criteria.userId?.let {
            sql.ln(AND, root.userId, EQUAL, param("userId", it))
        }
    }
}
