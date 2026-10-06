package br.com.wdc.shopping.domain.product

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.domain.codec.EntityGraph
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.criteria.CriterionCodec
import br.com.wdc.framework.domain.projection.ProjectionValues

/**
 * Como [Product] e [ProductCriteria] trafegam — o mesmo codec no cliente e no servidor.
 *
 * A imagem não entra: tem endpoint próprio.
 */
class ProductCodec : ModelCodec<Product, ProductCriteria> {

    override fun writeEntity(out: ExtensibleObjectOutput, entity: Product, graph: EntityGraph) {
        out.beginObject()
        entity.id?.let { out.name("id").value(it) }
        if (graph.track(entity)) {
            entity.name?.let { out.name("name").value(it) }
            entity.price?.let { out.name("price").value(it) }
            entity.description?.let { out.name("description").value(it) }
        }
        out.endObject()
    }

    override fun writeEntityProjected(out: ExtensibleObjectOutput, entity: Product, projection: Product) {
        out.beginObject()
        entity.id?.let { out.name("id").value(it) }
        if (projection.name != null) {
            out.name("name")
            entity.name?.let { out.value(it) } ?: out.nullValue()
        }
        if (projection.price != null) {
            out.name("price")
            entity.price?.let { out.value(it) } ?: out.nullValue()
        }
        if (projection.description != null) {
            out.name("description")
            entity.description?.let { out.value(it) } ?: out.nullValue()
        }
        out.endObject()
    }

    override fun computeProjection(newEntity: Product, oldEntity: Product): Product {
        val pv = ProjectionValues
        val projection = Product()
        if (newEntity.name != oldEntity.name) projection.name = pv.str
        if (newEntity.price != oldEntity.price) projection.price = pv.f64
        if (newEntity.description != oldEntity.description) projection.description = pv.str
        return projection
    }

    override fun readEntity(input: ExtensibleObjectInput): Product {
        val product = Product()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> product.id = InputCoerceUtils.asLong(input)
                "name" -> product.name = InputCoerceUtils.asString(input)
                "price" -> product.price = InputCoerceUtils.asDouble(input)
                "description" -> product.description = InputCoerceUtils.asString(input)
                else -> input.skipValue()
            }
        }
        input.endObject()
        return product
    }

    override fun readEntityForUpdate(input: ExtensibleObjectInput): ModelCodec.UpdateData<Product> {
        val pv = ProjectionValues
        val entity = Product()
        val projection = Product()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> { entity.id = InputCoerceUtils.asLong(input); projection.id = pv.i64 }
                "name" -> { entity.name = InputCoerceUtils.asString(input); projection.name = pv.str }
                "price" -> { entity.price = InputCoerceUtils.asDouble(input); projection.price = pv.f64 }
                "description" -> { entity.description = InputCoerceUtils.asString(input); projection.description = pv.str }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return ModelCodec.UpdateData(entity, projection)
    }

    override fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: ProductCriteria) {
        CriterionCodec.write(out, "productId", criteria.productId, CriterionCodec.LONG_OUT)
        CriterionCodec.write(out, "name", criteria.name, CriterionCodec.STRING_OUT)
        CriterionCodec.write(out, "price", criteria.price, CriterionCodec.DOUBLE_OUT)
        CriterionCodec.write(out, "description", criteria.description, CriterionCodec.STRING_OUT)
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
    }

    override fun readCriteriaField(input: ExtensibleObjectInput, fieldName: String, criteria: ProductCriteria): Boolean {
        when (fieldName) {
            "productId" -> CriterionCodec.read(input, criteria.productId, CriterionCodec.LONG_IN)
            "name" -> CriterionCodec.read(input, criteria.name, CriterionCodec.STRING_IN)
            "price" -> CriterionCodec.read(input, criteria.price, CriterionCodec.DOUBLE_IN)
            "description" -> CriterionCodec.read(input, criteria.description, CriterionCodec.STRING_IN)
            "orderBy" -> CriterionCodec.readOrderBy(input, ProductCriteria.OrderBy.entries)?.let { criteria.withOrderBy(it) }
            else -> return false
        }
        return true
    }

    override fun getProjection(criteria: ProductCriteria): Product? = criteria.projection

    override fun setGeneratedId(entity: Product, id: Long) {
        entity.id = id
    }
}
