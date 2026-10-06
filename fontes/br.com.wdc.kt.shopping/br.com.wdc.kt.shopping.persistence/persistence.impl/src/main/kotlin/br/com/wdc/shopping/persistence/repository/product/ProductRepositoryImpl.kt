package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.framework.domain.exception.BusinessException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.jooq.CriterionTranslator
import br.com.wdc.framework.jooq.JsonChildQueryBuilder
import br.com.wdc.framework.jooq.JsonQuery
import br.com.wdc.framework.jooq.JsonQueryBuilder
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductCriteria.OrderBy
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.persistence.repository.BaseRepositoryImpl
import br.com.wdc.shopping.persistence.scheme.sequences.SQ_PRODUCT
import br.com.wdc.shopping.persistence.scheme.tables.EnProduct
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PRODUCT
import java.math.BigDecimal
import org.jooq.Condition
import org.jooq.SortField
import org.jooq.impl.DSL

class ProductRepositoryImpl : BaseRepositoryImpl(), ProductRepository {

    override suspend fun insert(bean: Product): Boolean {
        val dsl = dsl()
        // o id informado é respeitado (a carga de demonstração grava ids fixos)
        val id = bean.id ?: dsl.nextval(SQ_PRODUCT).also { bean.id = it }
        val step = dsl.insertInto(EN_PRODUCT).set(EN_PRODUCT.ID, id)
        bean.name?.let { step.set(EN_PRODUCT.NAME, it) }
        bean.price?.let { step.set(EN_PRODUCT.PRICE, BigDecimal.valueOf(it)) }
        bean.description?.let { step.set(EN_PRODUCT.DESCRIPTION, it) }
        bean.image?.let { step.set(EN_PRODUCT.IMAGE, it) }
        return step.execute() > 0
    }

    override suspend fun update(newBean: Product, oldBean: Product?, projection: Product?): Boolean {
        val id = newBean.id ?: throw InvalidRequestException("update de produto exige o id")
        val prj = projection ?: newProjection()

        val step = dsl().update(EN_PRODUCT).set(EN_PRODUCT.ID, id)
        var hasChanges = false
        if (changed(newBean, oldBean, prj) { it.name }) {
            step.set(EN_PRODUCT.NAME, newBean.name)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.price }) {
            step.set(EN_PRODUCT.PRICE, newBean.price?.let(BigDecimal::valueOf))
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.description }) {
            step.set(EN_PRODUCT.DESCRIPTION, newBean.description)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.image }) {
            step.set(EN_PRODUCT.IMAGE, newBean.image)
            hasChanges = true
        }
        if (!hasChanges) {
            return false
        }
        return step.where(EN_PRODUCT.ID.eq(id)).execute() > 0
    }

    override suspend fun delete(criteria: ProductCriteria): Int {
        if (criteria.criterions().none { it.isSet() }) {
            throw InvalidRequestException("delete de produto exige ao menos um campo de critério informado")
        }
        return dsl().deleteFrom(EN_PRODUCT).where(conditions(EN_PRODUCT, criteria)).execute()
    }

    override suspend fun count(criteria: ProductCriteria): Int =
        dsl().selectCount().from(EN_PRODUCT).where(conditions(EN_PRODUCT, criteria)).fetchOne()!!.value1()

    override suspend fun fetch(criteria: ProductCriteria, offset: Int, limit: Int): List<Product> =
        QUERY.fetchToList(projectionFrom(criteria)) { t, q ->
            val step = q.where(conditions(t, criteria))
            step.orderBy(orderingOf(t, criteria))
            if (limit > 0) step.limit(limit)
            if (offset > 0) step.offset(offset)
        }

    override suspend fun fetchImage(productId: Long): ByteArray? {
        val projection = Product().apply { id = 0L; image = ByteArray(0) }
        val product = QUERY.fetchOne(projection) { t, q -> q.where(t.ID.eq(productId)) }
            ?: throw BusinessException("Product not found: $productId")
        return product.image
    }

    override suspend fun updateImage(productId: Long, image: ByteArray): Boolean =
        dsl().update(EN_PRODUCT).set(EN_PRODUCT.IMAGE, image).where(EN_PRODUCT.ID.eq(productId)).execute() > 0

    /** A projeção do critério, com o id garantido; sem projeção, a padrão. */
    private fun projectionFrom(criteria: ProductCriteria): Product {
        val projection = criteria.projection ?: return newProjection()
        if (projection.id == null) {
            projection.id = 0L
        }
        return projection
    }

    companion object {
        val QUERY: JsonQuery<Product, EnProduct> = JsonQueryBuilder<Product, EnProduct>()
            .setAlias("p")
            .setBeanFactory(::Product)
            .setTableFactory { alias -> EN_PRODUCT.`as`(alias) }
            .setDSLContextSupplier(BaseRepositoryImpl::dsl)
            .setOrdering(::orderingOf)
            .addI64("id", { it.id }, { b, v -> b.id = v }, { it.ID })
            .addStr("name", { it.name }, { b, v -> b.name = v }, { it.NAME })
            .addF64("price", { it.price }, { b, v -> b.price = v }, { it.PRICE })
            .addStr("description", { it.description }, { b, v -> b.description = v }, { it.DESCRIPTION })
            .addBin("image", { it.image }, { b, v -> b.image = v }, { it.IMAGE })
            .build()

        /** Toda ordenação por campo não único desempata pela chave. */
        fun orderingOf(t: EnProduct, criteria: Any?): List<SortField<*>> {
            val c = criteria as? ProductCriteria ?: return emptyList()
            return when (c.orderBy ?: return emptyList()) {
                OrderBy.OLDEST_FIRST -> listOf(t.ID.asc())
                OrderBy.NEWEST_FIRST -> listOf(t.ID.desc())
                OrderBy.NAME_A_TO_Z -> listOf(t.NAME.asc(), t.ID.asc())
                OrderBy.CHEAPEST_FIRST -> listOf(t.PRICE.asc(), t.ID.asc())
                OrderBy.MOST_EXPENSIVE_FIRST -> listOf(t.PRICE.desc(), t.ID.asc())
            }
        }

        /** Condição do critério do produto numa relação (o critério vem da coleção de projeção). */
        fun applyConditions(cq: JsonChildQueryBuilder<*, EnProduct>): Condition =
            cq.criteriaAs<ProductCriteria>()?.let { conditions(cq.childTable, it) } ?: DSL.noCondition()

        private fun conditions(t: EnProduct, criteria: ProductCriteria): Condition = CriterionTranslator.and(
            listOf(
                CriterionTranslator.translate(t.ID, criteria.productId),
                CriterionTranslator.translate(t.NAME, criteria.name),
                CriterionTranslator.translate(t.PRICE, criteria.price) { BigDecimal.valueOf(it) },
                CriterionTranslator.translate(t.DESCRIPTION, criteria.description),
            )
        )
    }
}
