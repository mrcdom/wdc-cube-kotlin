package br.com.wdc.store.persistence.repository.purchase

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.jooq.CriterionTranslator
import br.com.wdc.framework.jooq.JsonChildQueryBuilder
import br.com.wdc.framework.jooq.JsonQuery
import br.com.wdc.framework.jooq.JsonQueryBuilder
import br.com.wdc.store.domain.model.PlatformDateTime
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchase.PurchaseCriteria.OrderBy
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.persistence.repository.BaseRepositoryImpl
import br.com.wdc.store.persistence.repository.purchaseitem.PurchaseItemRepositoryImpl
import br.com.wdc.store.persistence.repository.user.UserRepositoryImpl
import br.com.wdc.store.persistence.scheme.sequences.SQ_PURCHASE
import br.com.wdc.store.persistence.scheme.tables.EnPurchase
import br.com.wdc.store.persistence.scheme.tables.references.EN_PURCHASE
import br.com.wdc.store.persistence.scheme.tables.references.EN_PURCHASEITEM
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.toJavaInstant
import org.jooq.Condition
import org.jooq.SortField
import org.jooq.impl.DSL

class PurchaseRepositoryImpl : BaseRepositoryImpl(), PurchaseRepository {

    /** Writes the purchase and, in cascade, the items that come with it. */
    /** Inserts the purchase and, if it went in, the items it carries (each with the item's default projection). */
    override suspend fun insert(bean: Purchase, projection: Purchase?): Boolean {
        if (bean.id == null) bean.id = dsl().nextval(SQ_PURCHASE)
        val inserted = QUERY.insert(bean, projection)

        if (inserted) {
            bean.items?.forEach { item ->
                item.purchase = bean
                PurchaseItemRepositoryImpl.insertRow(item, null)
            }
        }
        return inserted
    }

    /** Updates the purchase; the items are not touched — they have their own repository. */
    override suspend fun update(newBean: Purchase, oldBean: Purchase?, projection: Purchase?): Boolean {
        val id = newBean.id ?: throw InvalidRequestException("purchase update requires the id")
        val prj = projection ?: ownFieldsProjection()
        // no projection nor oldBean: compares with the empty entity, and only fields with a value go into the UPDATE
        val base = oldBean ?: if (projection == null) Purchase() else null

        val step = dsl().update(EN_PURCHASE).set(EN_PURCHASE.ID, id)
        var hasChanges = false
        if (changed(newBean, base, prj) { it.userId }) {
            step.set(EN_PURCHASE.USERID, newBean.userId)
            hasChanges = true
        }
        if (changed(newBean, base, prj) { it.buyDate }) {
            step.set(EN_PURCHASE.BUYDATE, newBean.buyDate?.let(::toColumn))
            hasChanges = true
        }
        if (!hasChanges) {
            return false
        }
        return step.where(EN_PURCHASE.ID.eq(id)).execute() > 0
    }

    /** Removes the purchases matching the criteria and, before them, their items. */
    override suspend fun delete(criteria: PurchaseCriteria): Int {
        if (criteria.criterions().none { it.isSet() }) {
            throw InvalidRequestException("purchase delete requires at least one criteria field set")
        }
        val dsl = dsl()
        // The keys are resolved first: the criteria may depend on the items (productId), which go first.
        val ids = dsl.select(EN_PURCHASE.ID).from(EN_PURCHASE).where(conditions(EN_PURCHASE, criteria, "xpi0"))
            .fetch(EN_PURCHASE.ID)
        if (ids.isEmpty()) {
            return 0
        }
        dsl.deleteFrom(EN_PURCHASEITEM).where(EN_PURCHASEITEM.PURCHASEID.`in`(ids)).execute()
        return dsl.deleteFrom(EN_PURCHASE).where(EN_PURCHASE.ID.`in`(ids)).execute()
    }

    override suspend fun count(criteria: PurchaseCriteria): Int =
        dsl().selectCount().from(EN_PURCHASE).where(conditions(EN_PURCHASE, criteria, "xpi0")).fetchOne()!!.value1()

    override suspend fun fetch(criteria: PurchaseCriteria, offset: Int, limit: Int): List<Purchase> =
        QUERY.fetchToList(projectionFrom(criteria)) { t, q ->
            val step = q.where(conditions(t, criteria, "xpi0"))
            step.orderBy(orderingOf(t, criteria))
            if (limit > 0) step.limit(limit)
            if (offset > 0) step.offset(offset)
        }

    /** The criteria's projection, with the id ensured; without one, the default. */
    private fun projectionFrom(criteria: PurchaseCriteria): Purchase {
        val projection = criteria.projection ?: return ownFieldsProjection()
        if (projection.id == null) {
            projection.id = 0L
        }
        return projection
    }

    companion object {
        val QUERY: JsonQuery<Purchase, EnPurchase> = JsonQueryBuilder<Purchase, EnPurchase>()
            .setAlias("p")
            .setBeanFactory(::Purchase)
            .setTableFactory { alias -> EN_PURCHASE.`as`(alias) }
            .setDSLContextSupplier(BaseRepositoryImpl::dsl)
            .setOrdering(::orderingOf)
            .addI64("id", { it.id }, { b, v -> b.id = v }, { it.ID })
            .addIlt("buyDate", { it.buyDate }, { b, v -> b.buyDate = v }, { it.BUYDATE })
            // the relations are declared on demand: purchase and item reference each other
            .lazy { qb ->
                qb.addBeanField(
                    "user", { it.user }, { b, v -> b.user = v }, UserRepositoryImpl.QUERY,
                    { cq ->
                        cq.where()
                            .and(cq.childTable.ID.eq(cq.superTable.USERID))
                            .and(UserRepositoryImpl.applyConditions(cq))
                    },
                    { key -> key.addI64("id") { it.USERID } },
                )
                qb.addBeanListField(
                    "items", { it.items }, { b, v -> b.items = v }, PurchaseItemRepositoryImpl.QUERY,
                ) { cq ->
                    cq.where()
                        .and(cq.childTable.PURCHASEID.eq(cq.superTable.ID))
                        .and(PurchaseItemRepositoryImpl.applyConditions(cq))
                }
            }
            .build()

        /** Every ordering by a non-unique field breaks ties by the key. */
        fun orderingOf(t: EnPurchase, criteria: Any?): List<SortField<*>> {
            val c = criteria as? PurchaseCriteria ?: return emptyList()
            return when (c.orderBy ?: return emptyList()) {
                OrderBy.OLDEST_FIRST -> listOf(t.ID.asc())
                OrderBy.NEWEST_FIRST -> listOf(t.ID.desc())
                OrderBy.MOST_RECENT_PURCHASE_FIRST -> listOf(t.BUYDATE.desc(), t.ID.desc())
                OrderBy.EARLIEST_PURCHASE_FIRST -> listOf(t.BUYDATE.asc(), t.ID.asc())
            }
        }

        /** Condition of the purchase criteria in a relation (the criteria comes from the projection collection). */
        fun applyConditions(cq: JsonChildQueryBuilder<*, EnPurchase>): Condition =
            cq.criteriaAs<PurchaseCriteria>()?.let { conditions(cq.childTable, it, cq.uniqueName("xpi")) } ?: DSL.noCondition()

        /** The column is TIMESTAMP without time zone; the convention is to store the instant in UTC. */
        private fun toColumn(instant: PlatformDateTime): LocalDateTime =
            LocalDateTime.ofInstant(instant.toJavaInstant(), ZoneOffset.UTC)

        private fun conditions(t: EnPurchase, criteria: PurchaseCriteria, itemAlias: String): Condition = CriterionTranslator.and(
            listOf(
                CriterionTranslator.translate(t.ID, criteria.purchaseId),
                CriterionTranslator.translate(t.USERID, criteria.userId),
                CriterionTranslator.translate(t.BUYDATE, criteria.buyDate, ::toColumn),
                existsItemMatching(t, criteria, itemAlias),
                CriterionTranslator.anyOf(criteria.anyOf) { conditions(t, it, itemAlias) },
            )
        )

        /**
         * Purchases containing the requested product. The product is not a purchase column — it is in the items;
         * the criteria is still a field like the others, and only where the condition applies changes.
         */
        private fun existsItemMatching(t: EnPurchase, criteria: PurchaseCriteria, itemAlias: String): Condition? {
            if (!criteria.hasProductId()) {
                return null
            }
            val item = EN_PURCHASEITEM.`as`(itemAlias)
            val onProduct = CriterionTranslator.translate(item.PRODUCTID, criteria.productId) ?: return null
            return DSL.exists(DSL.selectOne().from(item).where(item.PURCHASEID.eq(t.ID)).and(onProduct))
        }
    }
}
