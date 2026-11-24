package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.jooq.CriterionTranslator
import br.com.wdc.framework.jooq.JsonChildQueryBuilder
import br.com.wdc.framework.jooq.JsonQuery
import br.com.wdc.framework.jooq.JsonQueryBuilder
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria.OrderBy
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.persistence.repository.BaseRepositoryImpl
import br.com.wdc.shopping.persistence.repository.product.ProductRepositoryImpl
import br.com.wdc.shopping.persistence.repository.purchase.PurchaseRepositoryImpl
import br.com.wdc.shopping.persistence.scheme.sequences.SQ_PURCHASEITEM
import br.com.wdc.shopping.persistence.scheme.tables.EnPurchaseitem
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PRODUCT
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PURCHASE
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PURCHASEITEM
import java.math.BigDecimal
import org.jooq.Condition
import org.jooq.SortField
import org.jooq.impl.DSL

class PurchaseItemRepositoryImpl : BaseRepositoryImpl(), PurchaseItemRepository {

    override suspend fun insert(bean: PurchaseItem, projection: PurchaseItem?): Boolean = insertRow(bean, projection)

    override suspend fun update(newBean: PurchaseItem, oldBean: PurchaseItem?, projection: PurchaseItem?): Boolean {
        val id = newBean.id ?: throw InvalidRequestException("purchase item update requires the id")
        val prj = projection ?: ownFieldsProjection()
        // no projection nor oldBean: compares with the empty entity, and only fields with a value go into the UPDATE
        val base = oldBean ?: if (projection == null) PurchaseItem() else null

        val step = dsl().update(EN_PURCHASEITEM).set(EN_PURCHASEITEM.ID, id)
        var hasChanges = false
        if (changed(newBean, base, prj) { it.purchaseId }) {
            step.set(EN_PURCHASEITEM.PURCHASEID, newBean.purchaseId)
            hasChanges = true
        }
        if (changed(newBean, base, prj) { it.productId }) {
            step.set(EN_PURCHASEITEM.PRODUCTID, newBean.productId)
            hasChanges = true
        }
        if (changed(newBean, base, prj) { it.amount }) {
            newBean.amount?.let(::checkAmount)
            step.set(EN_PURCHASEITEM.AMOUNT, newBean.amount)
            hasChanges = true
        }
        if (changed(newBean, base, prj) { it.price }) {
            step.set(EN_PURCHASEITEM.PRICE, newBean.price?.let(BigDecimal::valueOf))
            hasChanges = true
        }
        if (!hasChanges) {
            return false
        }
        return step.where(EN_PURCHASEITEM.ID.eq(id)).execute() > 0
    }

    override suspend fun delete(criteria: PurchaseItemCriteria): Int {
        if (criteria.criterions().none { it.isSet() }) {
            throw InvalidRequestException("purchase item delete requires at least one criteria field set")
        }
        return dsl().deleteFrom(EN_PURCHASEITEM).where(conditions(EN_PURCHASEITEM, criteria, "xp0")).execute()
    }

    override suspend fun count(criteria: PurchaseItemCriteria): Int =
        dsl().selectCount().from(EN_PURCHASEITEM).where(conditions(EN_PURCHASEITEM, criteria, "xp0")).fetchOne()!!.value1()

    override suspend fun fetch(criteria: PurchaseItemCriteria, offset: Int, limit: Int): List<PurchaseItem> =
        QUERY.fetchToList(projectionFrom(criteria)) { t, q ->
            val step = q.where(conditions(t, criteria, "xp0"))
            step.orderBy(orderingOf(t, criteria))
            if (limit > 0) step.limit(limit)
            if (offset > 0) step.offset(offset)
        }

    /** The criteria's projection, with the id ensured; without one, the default. */
    private fun projectionFrom(criteria: PurchaseItemCriteria): PurchaseItem {
        val projection = criteria.projection ?: return ownFieldsProjection()
        if (projection.id == null) {
            projection.id = 0L
        }
        return projection
    }

    companion object {
        val QUERY: JsonQuery<PurchaseItem, EnPurchaseitem> = JsonQueryBuilder<PurchaseItem, EnPurchaseitem>()
            .setAlias("pi")
            .setBeanFactory(::PurchaseItem)
            .setTableFactory { alias -> EN_PURCHASEITEM.`as`(alias) }
            .setDSLContextSupplier(BaseRepositoryImpl::dsl)
            .setOrdering(::orderingOf)
            .addI64("id", { it.id }, { b, v -> b.id = v }, { it.ID })
            .addI32("amount", { it.amount }, { b, v -> b.amount = v }, { it.AMOUNT })
            .addF64("price", { it.price }, { b, v -> b.price = v }, { it.PRICE })
            // the relations are declared on demand: purchase and item reference each other
            .lazy { qb ->
                qb.addBeanField(
                    "purchase", { it.purchase }, { b, v -> b.purchase = v }, PurchaseRepositoryImpl.QUERY,
                    { cq ->
                        cq.where()
                            .and(cq.childTable.ID.eq(cq.superTable.PURCHASEID))
                            .and(PurchaseRepositoryImpl.applyConditions(cq))
                    },
                    { key -> key.addI64("id") { it.PURCHASEID } },
                )
                qb.addBeanField(
                    "product", { it.product }, { b, v -> b.product = v }, ProductRepositoryImpl.QUERY,
                    { cq ->
                        cq.where()
                            .and(cq.childTable.ID.eq(cq.superTable.PRODUCTID))
                            .and(ProductRepositoryImpl.applyConditions(cq))
                    },
                    { key -> key.addI64("id") { it.PRODUCTID } },
                )
            }
            .build()

        /** Every ordering by a non-unique field breaks ties by the key. */
        fun orderingOf(t: EnPurchaseitem, criteria: Any?): List<SortField<*>> {
            val c = criteria as? PurchaseItemCriteria ?: return emptyList()
            return when (c.orderBy ?: return emptyList()) {
                OrderBy.OLDEST_FIRST -> listOf(t.ID.asc())
                OrderBy.NEWEST_FIRST -> listOf(t.ID.desc())
                OrderBy.MOST_EXPENSIVE_FIRST -> listOf(t.PRICE.desc(), t.ID.asc())
                OrderBy.LARGEST_QUANTITY_FIRST -> listOf(t.AMOUNT.desc(), t.ID.asc())
            }
        }

        /** Condition of the item criteria in a relation (the criteria comes from the projection collection). */
        fun applyConditions(cq: JsonChildQueryBuilder<*, EnPurchaseitem>): Condition =
            cq.criteriaAs<PurchaseItemCriteria>()?.let { conditions(cq.childTable, it, cq.uniqueName("xp")) } ?: DSL.noCondition()

        /** Writes the item row — also used by the purchase insert cascade. */
        /**
         * Inserts an item (alone or with its purchase). **Its price is the product's, read here**, whatever the
         * caller sent: the server prices every item, on every path (in process, REST, checkout). An item without a
         * product, or of one that does not exist, is refused.
         */
        internal fun insertRow(bean: PurchaseItem, projection: PurchaseItem?): Boolean {
            bean.amount?.let(::checkAmount)
            bean.price = productPrice(bean.productId)
            projection?.let { if (it.price == null) it.price = ProjectionValues.f64 }
            if (bean.id == null) bean.id = BaseRepositoryImpl.dsl().nextval(SQ_PURCHASEITEM)
            return QUERY.insert(bean, projection)
        }

        private fun productPrice(productId: Long?): Double {
            val id = productId ?: throw InvalidRequestException("purchase item without the product")
            val row = BaseRepositoryImpl.dsl().select(EN_PRODUCT.PRICE).from(EN_PRODUCT).where(EN_PRODUCT.ID.eq(id))
                .fetchOne() ?: throw InvalidRequestException("unknown product: $id")
            return row.value1()?.toDouble() ?: throw InvalidRequestException("product $id has no price")
        }

        private fun checkAmount(amount: Int) {
            if (amount <= 0) {
                throw InvalidRequestException("the item quantity must be greater than zero")
            }
        }

        private fun conditions(t: EnPurchaseitem, criteria: PurchaseItemCriteria, purchaseAlias: String): Condition =
            CriterionTranslator.and(
                listOf(
                    CriterionTranslator.translate(t.ID, criteria.purchaseItemId),
                    CriterionTranslator.translate(t.PURCHASEID, criteria.purchaseId),
                    CriterionTranslator.translate(t.PRODUCTID, criteria.productId),
                    CriterionTranslator.translate(t.AMOUNT, criteria.amount),
                    CriterionTranslator.translate(t.PRICE, criteria.price) { BigDecimal.valueOf(it) },
                    existsPurchaseOfUser(t, criteria, purchaseAlias),
                    CriterionTranslator.anyOf(criteria.anyOf) { conditions(t, it, purchaseAlias) },
                )
            )

        /**
         * Items whose purchases belong to the requested user. The user owns the purchase, not the item: the
         * condition applies to the purchase, correlated by the foreign key.
         */
        private fun existsPurchaseOfUser(t: EnPurchaseitem, criteria: PurchaseItemCriteria, purchaseAlias: String): Condition? {
            if (!criteria.hasUserId()) {
                return null
            }
            val purchase = EN_PURCHASE.`as`(purchaseAlias)
            val onUser = CriterionTranslator.translate(purchase.USERID, criteria.userId) ?: return null
            return DSL.exists(DSL.selectOne().from(purchase).where(purchase.ID.eq(t.PURCHASEID)).and(onUser))
        }
    }
}
