package br.com.wdc.shopping.persistence.repository.purchase

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.jooq.CriterionTranslator
import br.com.wdc.framework.jooq.JsonChildQueryBuilder
import br.com.wdc.framework.jooq.JsonQuery
import br.com.wdc.framework.jooq.JsonQueryBuilder
import br.com.wdc.shopping.domain.model.PlatformDateTime
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria.OrderBy
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.persistence.repository.BaseRepositoryImpl
import br.com.wdc.shopping.persistence.repository.purchaseitem.PurchaseItemRepositoryImpl
import br.com.wdc.shopping.persistence.repository.user.UserRepositoryImpl
import br.com.wdc.shopping.persistence.scheme.sequences.SQ_PURCHASE
import br.com.wdc.shopping.persistence.scheme.tables.EnPurchase
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PURCHASE
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PURCHASEITEM
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.toJavaInstant
import org.jooq.Condition
import org.jooq.SortField
import org.jooq.impl.DSL

class PurchaseRepositoryImpl : BaseRepositoryImpl(), PurchaseRepository {

    /** Grava a compra e, em cascata, os itens que vierem com ela. */
    override suspend fun insert(bean: Purchase): Boolean {
        val dsl = dsl()
        val id = bean.id ?: dsl.nextval(SQ_PURCHASE).also { bean.id = it }
        val step = dsl.insertInto(EN_PURCHASE).set(EN_PURCHASE.ID, id)
        bean.userId?.let { step.set(EN_PURCHASE.USERID, it) }
        bean.buyDate?.let { step.set(EN_PURCHASE.BUYDATE, toColumn(it)) }
        val inserted = step.execute() > 0

        if (inserted) {
            bean.items?.forEach { item ->
                item.purchase = bean
                PurchaseItemRepositoryImpl.insertRow(dsl, item)
            }
        }
        return inserted
    }

    /** Atualiza a compra; os itens não são tocados — têm repositório próprio. */
    override suspend fun update(newBean: Purchase, oldBean: Purchase?, projection: Purchase?): Boolean {
        val id = newBean.id ?: throw InvalidRequestException("update de compra exige o id")
        val prj = projection ?: newProjection()

        val step = dsl().update(EN_PURCHASE).set(EN_PURCHASE.ID, id)
        var hasChanges = false
        if (changed(newBean, oldBean, prj) { it.userId }) {
            step.set(EN_PURCHASE.USERID, newBean.userId)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.buyDate }) {
            step.set(EN_PURCHASE.BUYDATE, newBean.buyDate?.let(::toColumn))
            hasChanges = true
        }
        if (!hasChanges) {
            return false
        }
        return step.where(EN_PURCHASE.ID.eq(id)).execute() > 0
    }

    /** Remove as compras do critério e, antes delas, os seus itens. */
    override suspend fun delete(criteria: PurchaseCriteria): Int {
        if (criteria.criterions().none { it.isSet() }) {
            throw InvalidRequestException("delete de compra exige ao menos um campo de critério informado")
        }
        val dsl = dsl()
        // As chaves são resolvidas antes: o critério pode depender dos itens (productId), que saem primeiro.
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

    /** A projeção do critério, com o id garantido; sem projeção, a padrão. */
    private fun projectionFrom(criteria: PurchaseCriteria): Purchase {
        val projection = criteria.projection ?: return newProjection()
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
            .addInstantFromLdt("buyDate", { it.buyDate }, { b, v -> b.buyDate = v }, { it.BUYDATE })
            // as relações são declaradas sob demanda: compra e item referenciam-se mutuamente
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

        /** Toda ordenação por campo não único desempata pela chave. */
        fun orderingOf(t: EnPurchase, criteria: Any?): List<SortField<*>> {
            val c = criteria as? PurchaseCriteria ?: return emptyList()
            return when (c.orderBy ?: return emptyList()) {
                OrderBy.OLDEST_FIRST -> listOf(t.ID.asc())
                OrderBy.NEWEST_FIRST -> listOf(t.ID.desc())
                OrderBy.MOST_RECENT_PURCHASE_FIRST -> listOf(t.BUYDATE.desc(), t.ID.desc())
                OrderBy.EARLIEST_PURCHASE_FIRST -> listOf(t.BUYDATE.asc(), t.ID.asc())
            }
        }

        /** Condição do critério da compra numa relação (o critério vem da coleção de projeção). */
        fun applyConditions(cq: JsonChildQueryBuilder<*, EnPurchase>): Condition =
            cq.criteriaAs<PurchaseCriteria>()?.let { conditions(cq.childTable, it, cq.uniqueName("xpi")) } ?: DSL.noCondition()

        /** A coluna é TIMESTAMP sem fuso; a convenção é guardar o instante em UTC. */
        private fun toColumn(instant: PlatformDateTime): LocalDateTime =
            LocalDateTime.ofInstant(instant.toJavaInstant(), ZoneOffset.UTC)

        private fun conditions(t: EnPurchase, criteria: PurchaseCriteria, itemAlias: String): Condition = CriterionTranslator.and(
            listOf(
                CriterionTranslator.translate(t.ID, criteria.purchaseId),
                CriterionTranslator.translate(t.USERID, criteria.userId),
                CriterionTranslator.translate(t.BUYDATE, criteria.buyDate, ::toColumn),
                existsItemMatching(t, criteria, itemAlias),
            )
        )

        /**
         * Compras que contêm o produto pedido. O produto não é coluna da compra — está nos itens; o critério
         * continua sendo um campo como os outros, e só muda onde a condição incide.
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
