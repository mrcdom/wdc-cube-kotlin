package br.com.wdc.shopping.persistence.repository.purchase

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.utils.ProjectionList
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.repository.product.FetchProductsCmd
import br.com.wdc.shopping.persistence.repository.purchaseitem.FetchPurchaseItemsCmd
import br.com.wdc.shopping.persistence.repository.user.FetchUsersCmd
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.sql.SqlList
import kotlinx.datetime.toKotlinInstant
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import org.jdbi.v3.core.Jdbi
import java.io.StringReader
import java.sql.Connection

class FetchPurchaseCmd : BaseCommand() {

    companion object {
        fun byId(connection: Connection, purchaseId: Long, projection: Purchase?): Purchase? {
            val list = byCriteria(connection, PurchaseCriteria()
                .withPurchaseId(purchaseId)
                .withProjection(projection))
            return list.firstOrNull()
        }

        fun byCriteria(connection: Connection, criteria: PurchaseCriteria): List<Purchase> =
            FetchPurchaseCmd().execute(connection, criteria)

        fun fields(prj: Purchase?, en: EnPurchase): List<DbField> {
            val pv = ProjectionValues
            var p = prj
            if (p == null) {
                p = Purchase()
                p.buyDate = pv.offsetDateTime
            }
            p.id = pv.i64

            val fields = mutableListOf<DbField>()
            fields.add(en.id)
            if (p.buyDate != null) fields.add(en.buyDate)
            if (p.user != null) fields.add(en.userId)
            return fields
        }

        fun fromJson(json: String, purchaseMap: MutableMap<Long, Purchase>, userMap: MutableMap<Long, User>): Purchase {
            JsonReader(StringReader(json)).use { reader ->
                val row = EnPurchase.Row.parseJson(reader)

                val purchase = purchaseMap.getOrPut(row.id!!) {
                    Purchase().also { it.id = row.id }
                }

                if (purchase.buyDate == null) purchase.buyDate = row.buyDate?.toInstant()?.toKotlinInstant()

                if (row.userId != null && purchase.user == null) {
                    purchase.user = userMap.getOrPut(row.userId!!) {
                        User().also { it.id = row.userId }
                    }
                }

                return purchase
            }
        }
    }

    // :: CTE fields
    protected var ctePurchase: EnPurchase? = null
    protected var purchasePrj: Purchase? = null
    protected var ctePurchaseItem: EnPurchaseItem? = null
    protected var purchaseItemPrj: PurchaseItem? = null
    protected var cteProduct: EnProduct? = null
    protected var productPrj: Product? = null
    protected var cteUser: EnUser? = null
    protected var userPrj: User? = null

    fun execute(connection: Connection, criteria: PurchaseCriteria): List<Purchase> {
        val crit = criteria

        val sql = buildCte(crit)

        var fJsonData = "json_data"
        var unionAll = "         "

        val cteUserId = 1
        if (userPrj != null) {
            val expr = SqlUtils.toJsonField(FetchUsersCmd.fields(userPrj, cteUser!!))
            sql.ln(unionAll, SELECT, "$cteUserId,", expr, AS, fJsonData, FROM, cteUser!!.alias)
            unionAll = UNION_ALL
        }

        val cteProductId = 2
        if (cteProduct != null) {
            val expr = SqlUtils.toJsonField(FetchProductsCmd.fields(productPrj, cteProduct!!))
            sql.ln(unionAll, SELECT, "$cteProductId,", expr, AS, fJsonData, FROM, cteProduct!!.alias)
            unionAll = UNION_ALL
        }

        val ctePurchaseId = 3
        if (ctePurchase != null) {
            val expr = SqlUtils.toJsonField(fields(purchasePrj, ctePurchase!!))
            sql.ln(unionAll, SELECT, "$ctePurchaseId,", expr, AS, fJsonData, FROM, ctePurchase!!.alias)
            unionAll = UNION_ALL
        }

        val ctePurchaseItemId = 4
        if (ctePurchaseItem != null) {
            val expr = SqlUtils.toJsonField(FetchPurchaseItemsCmd.fields(purchaseItemPrj, ctePurchaseItem!!))
            sql.ln(unionAll, SELECT, "$ctePurchaseItemId,", expr, AS, fJsonData, FROM, ctePurchaseItem!!.alias)
        }

        val userMap = mutableMapOf<Long, User>()
        val productMap = mutableMapOf<Long, Product>()
        val purchaseMap = mutableMapOf<Long, Purchase>()
        val purchaseItemMap = mutableMapOf<Long, PurchaseItem>()
        val purchaseList = mutableListOf<Purchase>()

        Jdbi.create(connection).open().use { handle ->
            val query = handle.createQuery(sql.toText())
            applyParams(query)

            query.map { rs, _ ->
                val cteId = rs.getInt(1)
                val jsonData = rs.getString(2)

                when (cteId) {
                    cteUserId -> FetchUsersCmd.fromJson(jsonData, userMap)
                    cteProductId -> FetchProductsCmd.fromJson(jsonData, productMap)
                    ctePurchaseId -> purchaseList.add(fromJson(jsonData, purchaseMap, userMap))
                    ctePurchaseItemId -> FetchPurchaseItemsCmd.fromJson(jsonData, purchaseItemMap, purchaseMap, productMap)
                }
                true
            }.forEach { _ -> }
        }

        return purchaseList
    }

    protected fun safeProjection(prj: Purchase?): Purchase {
        val pv = ProjectionValues
        var p = prj
        if (p == null) {
            p = Purchase()
            p.buyDate = pv.offsetDateTime
        }
        p.id = pv.i64

        if (p.user != null) {
            p.user!!.id = pv.i64
        }

        return p
    }

    private fun buildCte(criteria: PurchaseCriteria): SqlList {
        val ident = "  "
        val sql = SqlList()

        ctePurchase = EnPurchase("ctePurchase")
        purchasePrj = safeProjection(criteria.projection)

        sql.ln(WITH, ctePurchase!!.alias, AS, '(')
        sql.ln(ctePurchase(criteria, purchasePrj, null, null).toText(ident))
        sql.ln(")")

        if (purchasePrj!!.user != null) {
            userPrj = purchasePrj!!.user
            cteUser = EnUser("cteUser")

            sql.ln(",", cteUser!!.alias, AS, '(')
            sql.ln(cteUser(userPrj!!, ctePurchase!!).toText(ident))
            sql.ln(")")
        }

        if (purchasePrj!!.items != null && purchasePrj!!.items!!.isNotEmpty()) {
            purchaseItemPrj = purchasePrj!!.items!![0]

            purchaseItemPrj!!.purchase = Purchase()

            var itemCriteria: PurchaseItemCriteria? = null
            val items = purchasePrj!!.items
            if (items is ProjectionList<*>) {
                @Suppress("UNCHECKED_CAST")
                itemCriteria = items.criteria as? PurchaseItemCriteria
            }

            ctePurchaseItem = EnPurchaseItem("ctePurchaseItem")
            sql.ln(",", ctePurchaseItem!!.alias, AS, '(')
            sql.ln(ctePurchaseItem(itemCriteria, purchaseItemPrj!!, ctePurchase!!).toText(ident))
            sql.ln(")")

            if (purchaseItemPrj!!.product != null) {
                cteProduct = EnProduct("cteProduct")
                productPrj = purchaseItemPrj!!.product

                sql.ln(",", cteProduct!!.alias, AS, '(')
                sql.ln(cteProduct(productPrj!!, ctePurchaseItem!!).toText(ident))
                sql.ln(")")
            }
        }

        return sql
    }

    fun ctePurchase(criteria: PurchaseCriteria?, prj: Purchase?, superAlias: String?, superId: DbField?): SqlList {
        val b = EnPurchase("B")

        val sql = SqlList()
        sql.ln(SELECT)
        fields(prj, b).forEach { sql.field(it) }
        sql.ln(FROM, b.tableRef())
        sql.ln(WHERE_TRUE)

        if (superAlias != null) {
            sql.ln(AND, EXISTS { ll ->
                ll.ln(SELECT, 1)
                ll.ln(FROM, superAlias)
                ll.ln(WHERE, superId, EQUAL, b.id)
            })
        }

        if (criteria == null) return sql

        val applier = ApplyPurchaseCriteria(this)
        applier.criteria = criteria
        applier.root = b
        applier.apply(sql)

        criteria.orderBy?.let {
            when (it) {
                PurchaseCriteria.OrderBy.ACENDING -> sql.ln(ORDER_BY(b.id.asc()))
                PurchaseCriteria.OrderBy.DESCENDING -> sql.ln(ORDER_BY(b.id.desc()))
            }
        }

        criteria.limit?.let { sql.ln(LIMIT, it) }
        criteria.offset?.let { sql.ln(OFFSET, it) }

        return sql
    }

    private fun ctePurchaseItem(criteria: PurchaseItemCriteria?, prj: PurchaseItem, owner: EnPurchase): SqlList {
        val child = FetchPurchaseItemsCmd()
        val sql = child.ctePurchaseItem(criteria, prj, owner.alias, owner.id)
        child.transferParamsTo(this)
        return sql
    }

    private fun cteProduct(prj: Product, owner: EnPurchaseItem): SqlList {
        val child = FetchProductsCmd()
        val sql = child.cteProduct(null, prj, owner.alias, owner.productId)
        child.transferParamsTo(this)
        return sql
    }

    private fun cteUser(prj: User, owner: EnPurchase): SqlList {
        val child = FetchUsersCmd()
        val sql = child.cteUser(null, prj, owner.alias, owner.userId)
        child.transferParamsTo(this)
        return sql
    }
}
