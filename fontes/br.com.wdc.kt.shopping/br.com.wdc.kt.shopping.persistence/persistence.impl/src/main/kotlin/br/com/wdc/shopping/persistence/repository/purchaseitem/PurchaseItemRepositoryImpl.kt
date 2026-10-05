package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.repository.Repository.Companion.changed
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
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PURCHASE
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PURCHASEITEM
import java.math.BigDecimal
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL

class PurchaseItemRepositoryImpl : BaseRepositoryImpl(), PurchaseItemRepository {

    override suspend fun insert(bean: PurchaseItem): Boolean = insertRow(dsl(), bean)

    override suspend fun update(newBean: PurchaseItem, oldBean: PurchaseItem?, projection: PurchaseItem?): Boolean {
        val id = newBean.id ?: throw InvalidRequestException("update de item de compra exige o id")
        val prj = projection ?: newProjection()

        val step = dsl().update(EN_PURCHASEITEM).set(EN_PURCHASEITEM.ID, id)
        var hasChanges = false
        if (changed(newBean, oldBean, prj) { it.purchaseId }) {
            step.set(EN_PURCHASEITEM.PURCHASEID, newBean.purchaseId)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.productId }) {
            step.set(EN_PURCHASEITEM.PRODUCTID, newBean.productId)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.amount }) {
            newBean.amount?.let(::checkAmount)
            step.set(EN_PURCHASEITEM.AMOUNT, newBean.amount)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.price }) {
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
            throw InvalidRequestException("delete de item de compra exige ao menos um campo de critério informado")
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

    /** A projeção do critério, com o id garantido; sem projeção, a padrão. */
    private fun projectionFrom(criteria: PurchaseItemCriteria): PurchaseItem {
        val projection = criteria.projection ?: return newProjection()
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
            // as relações são declaradas sob demanda: compra e item referenciam-se mutuamente
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

        /** Toda ordenação por campo não único desempata pela chave. */
        fun orderingOf(t: EnPurchaseitem, criteria: Any?): List<SortField<*>> {
            val c = criteria as? PurchaseItemCriteria ?: return emptyList()
            return when (c.orderBy ?: return emptyList()) {
                OrderBy.OLDEST_FIRST -> listOf(t.ID.asc())
                OrderBy.NEWEST_FIRST -> listOf(t.ID.desc())
                OrderBy.MOST_EXPENSIVE_FIRST -> listOf(t.PRICE.desc(), t.ID.asc())
                OrderBy.LARGEST_QUANTITY_FIRST -> listOf(t.AMOUNT.desc(), t.ID.asc())
            }
        }

        /** Condição do critério do item numa relação (o critério vem da coleção de projeção). */
        fun applyConditions(cq: JsonChildQueryBuilder<*, EnPurchaseitem>): Condition =
            cq.criteriaAs<PurchaseItemCriteria>()?.let { conditions(cq.childTable, it, cq.uniqueName("xp")) } ?: DSL.noCondition()

        /** Grava a linha do item — também usado pela cascata do insert da compra. */
        internal fun insertRow(dsl: DSLContext, bean: PurchaseItem): Boolean {
            bean.amount?.let(::checkAmount)
            val id = bean.id ?: dsl.nextval(SQ_PURCHASEITEM).also { bean.id = it }
            val step = dsl.insertInto(EN_PURCHASEITEM).set(EN_PURCHASEITEM.ID, id)
            bean.purchaseId?.let { step.set(EN_PURCHASEITEM.PURCHASEID, it) }
            bean.productId?.let { step.set(EN_PURCHASEITEM.PRODUCTID, it) }
            bean.amount?.let { step.set(EN_PURCHASEITEM.AMOUNT, it) }
            bean.price?.let { step.set(EN_PURCHASEITEM.PRICE, BigDecimal.valueOf(it)) }
            return step.execute() > 0
        }

        private fun checkAmount(amount: Int) {
            if (amount <= 0) {
                throw InvalidRequestException("a quantidade do item deve ser maior que zero")
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
                )
            )

        /**
         * Itens cujas compras são do usuário pedido. O usuário é dono da compra, não do item: a condição
         * incide sobre a compra, correlacionada pela chave estrangeira.
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
