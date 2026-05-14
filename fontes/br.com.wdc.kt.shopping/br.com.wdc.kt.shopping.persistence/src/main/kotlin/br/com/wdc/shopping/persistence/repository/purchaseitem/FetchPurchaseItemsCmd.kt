package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.persistence.repository.BaseCommand
import br.com.wdc.shopping.persistence.repository.product.FetchProductsCmd
import br.com.wdc.shopping.persistence.repository.purchase.FetchPurchaseCmd
import br.com.wdc.shopping.persistence.repository.user.FetchUsersCmd
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import com.google.gson.stream.JsonReader
import org.jdbi.v3.core.Jdbi
import java.io.StringReader
import java.sql.Connection

class FetchPurchaseItemsCmd : BaseCommand() {

    companion object {
        fun byId(connection: Connection, purchaseItemId: Long, projection: PurchaseItem?): PurchaseItem? {
            val list = FetchPurchaseItemsCmd().execute(connection, PurchaseItemCriteria()
                .withPurchaseItemId(purchaseItemId)
                .withProjection(projection))
            return list.firstOrNull()
        }

        fun byCriteria(connection: Connection, criteria: PurchaseItemCriteria): List<PurchaseItem> =
            FetchPurchaseItemsCmd().execute(connection, criteria)

        fun fields(prj: PurchaseItem?, en: EnPurchaseItem): List<DbField> {
            val pv = ProjectionValues
            var p = prj
            if (p == null) {
                p = PurchaseItem()
                p.amount = pv.i32
                p.price = pv.f64
            }
            p.id = pv.i64

            val fields = mutableListOf<DbField>()
            if (p.id != null) fields.add(en.id)
            if (p.amount != null) fields.add(en.amount)
            if (p.price != null) fields.add(en.price)
            if (p.product != null) fields.add(en.productId)
            if (p.purchase != null) fields.add(en.purchaseId)
            return fields
        }

        fun fromJson(
            json: String,
            purchaseItemMap: MutableMap<Long, PurchaseItem>,
            purchaseMap: MutableMap<Long, Purchase>,
            productMap: MutableMap<Long, Product>,
        ): PurchaseItem {
            JsonReader(StringReader(json)).use { reader ->
                val row = EnPurchaseItem.Row.parseJson(reader)

                val purchaseItem = purchaseItemMap.getOrPut(row.id!!) {
                    PurchaseItem().also { it.id = row.id }
                }

                if (purchaseItem.amount == null) purchaseItem.amount = row.amount
                if (purchaseItem.price == null) purchaseItem.price = CoerceUtils.asDouble(row.price)

                if (row.purchaseId != null && purchaseItem.purchase == null) {
                    purchaseItem.purchase = purchaseMap.getOrPut(row.purchaseId!!) {
                        Purchase().also { it.id = row.purchaseId }
                    }

                    if (purchaseItem.purchase!!.items == null) {
                        purchaseItem.purchase!!.items = mutableListOf()
                    }
                    purchaseItem.purchase!!.items!!.add(purchaseItem)
                }

                if (row.productId != null && purchaseItem.product == null) {
                    purchaseItem.product = productMap.getOrPut(row.productId!!) {
                        Product().also { it.id = row.productId }
                    }
                }

                return purchaseItem
            }
        }
    }

    // :: CTE fields
    protected var ctePurchaseItem: EnPurchaseItem? = null
    protected var purchaseItemPrj: PurchaseItem? = null
    protected var cteProduct: EnProduct? = null
    protected var productPrj: Product? = null
    protected var ctePurchase: EnPurchase? = null
    protected var purchasePrj: Purchase? = null
    protected var cteUser: EnUser? = null
    protected var userPrj: User? = null

    fun execute(connection: Connection, criteria: PurchaseItemCriteria): List<PurchaseItem> {
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
            val expr = SqlUtils.toJsonField(FetchPurchaseCmd.fields(purchasePrj, ctePurchase!!))
            sql.ln(unionAll, SELECT, "$ctePurchaseId,", expr, AS, fJsonData, FROM, ctePurchase!!.alias)
            unionAll = UNION_ALL
        }

        val ctePurchaseItemId = 4
        if (ctePurchaseItem != null) {
            val expr = SqlUtils.toJsonField(fields(purchaseItemPrj, ctePurchaseItem!!))
            sql.ln(unionAll, SELECT, "$ctePurchaseItemId,", expr, AS, fJsonData, FROM, ctePurchaseItem!!.alias)
        }

        val userMap = mutableMapOf<Long, User>()
        val productMap = mutableMapOf<Long, Product>()
        val purchaseMap = mutableMapOf<Long, Purchase>()
        val purchaseItemMap = mutableMapOf<Long, PurchaseItem>()
        val purchaseItemList = mutableListOf<PurchaseItem>()

        Jdbi.create(connection).open().use { handle ->
            val query = handle.createQuery(sql.toText())
            applyParams(query)

            query.map { rs, _ ->
                val cteId = rs.getInt(1)
                val jsonData = rs.getString(2)

                when (cteId) {
                    cteUserId -> FetchUsersCmd.fromJson(jsonData, userMap)
                    cteProductId -> FetchProductsCmd.fromJson(jsonData, productMap)
                    ctePurchaseId -> FetchPurchaseCmd.fromJson(jsonData, purchaseMap, userMap)
                    ctePurchaseItemId -> purchaseItemList.add(fromJson(jsonData, purchaseItemMap, purchaseMap, productMap))
                }
                true
            }.forEach { _ -> }
        }

        return purchaseItemList
    }

    private fun safeProjection(prj: PurchaseItem?): PurchaseItem {
        val pv = ProjectionValues
        var p = prj
        if (p == null) {
            p = PurchaseItem()
            p.amount = pv.i32
            p.price = pv.f64
        }
        p.id = pv.i64
        return p
    }

    private fun buildCte(criteria: PurchaseItemCriteria): SqlList {
        val ident = "  "
        val sql = SqlList()

        ctePurchaseItem = EnPurchaseItem("ctePurchaseItem")
        purchaseItemPrj = safeProjection(criteria.projection)

        sql.ln(WITH, ctePurchaseItem!!.alias, AS, '(')
        sql.ln(ctePurchaseItem(criteria, purchaseItemPrj, null, null).toText(ident))
        sql.ln(")")

        if (purchaseItemPrj!!.product != null) {
            cteProduct = EnProduct("cteProduct")
            productPrj = purchaseItemPrj!!.product

            sql.ln(",", cteProduct!!.alias, AS, '(')
            sql.ln(cteProduct(productPrj!!, ctePurchaseItem!!).toText(ident))
            sql.ln(")")
        }

        if (purchaseItemPrj!!.purchase != null) {
            ctePurchase = EnPurchase("ctePurchase")
            purchasePrj = purchaseItemPrj!!.purchase

            purchasePrj!!.items = null

            sql.ln(",", ctePurchase!!.alias, AS, '(')
            sql.ln(ctePurchase(purchasePrj!!, ctePurchaseItem!!).toText(ident))
            sql.ln(")")

            if (purchasePrj!!.user != null) {
                userPrj = purchasePrj!!.user
                cteUser = EnUser("cteUser")

                sql.ln(",", cteUser!!.alias, AS, '(')
                sql.ln(cteUser(userPrj!!, ctePurchase!!).toText(ident))
                sql.ln(")")
            }
        }

        return sql
    }

    fun ctePurchaseItem(criteria: PurchaseItemCriteria?, prj: PurchaseItem?, ownerAlias: String?, ownerId: DbField?): SqlList {
        val pi = EnPurchaseItem("PI")

        val sql = SqlList()
        sql.ln(SELECT)
        fields(prj, pi).forEach { sql.field(it) }
        sql.ln(FROM, pi.tableRef())
        sql.ln(WHERE_TRUE)

        if (ownerAlias != null) {
            sql.ln(AND, EXISTS { ll ->
                ll.ln(SELECT, 1)
                ll.ln(FROM, ownerAlias)
                ll.ln(WHERE, ownerId, EQUAL, pi.purchaseId)
            })
        }

        if (criteria == null) return sql

        val applier = ApplyPurchaseItemCriteria(this)
        applier.criteria = criteria
        applier.root = pi
        applier.apply(sql)

        criteria.orderBy?.let {
            when (it) {
                PurchaseItemCriteria.OrderBy.ASCENDING -> sql.ln(ORDER_BY(pi.id.asc()))
                PurchaseItemCriteria.OrderBy.DESCENDING -> sql.ln(ORDER_BY(pi.id.desc()))
            }
        }

        criteria.limit?.let { sql.ln(LIMIT, it) }
        criteria.offset?.let { sql.ln(OFFSET, it) }

        return sql
    }

    private fun cteProduct(prj: Product, superEn: EnPurchaseItem): SqlList {
        val child = FetchProductsCmd()
        val sql = child.cteProduct(null, prj, superEn.alias, superEn.productId)
        child.transferParamsTo(this)
        return sql
    }

    fun ctePurchase(prj: Purchase, superEn: EnPurchaseItem): SqlList {
        val pv = ProjectionValues
        if (prj.user != null) {
            prj.user!!.id = pv.i64
        }

        val child = FetchPurchaseCmd()
        val sql = child.ctePurchase(null, prj, superEn.alias, superEn.purchaseId)
        child.transferParamsTo(this)
        return sql
    }

    private fun cteUser(prj: User, superEn: EnPurchase): SqlList {
        val child = FetchUsersCmd()
        val sql = child.cteUser(null, prj, superEn.alias, superEn.userId)
        child.transferParamsTo(this)
        return sql
    }
}
