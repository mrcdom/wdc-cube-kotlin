package br.com.wdc.shopping.domain.purchaseitem

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.framework.domain.codec.EntityGraph
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.criteria.CriterionCodec
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCodec
import br.com.wdc.shopping.domain.purchase.Purchase

/**
 * Como [PurchaseItem] e [PurchaseItemCriteria] trafegam — o mesmo codec no cliente e no servidor.
 *
 * A compra do item trafega só pela chave (`purchaseId`): do outro lado ela é reconstituída como stub. O
 * produto vai aninhado.
 */
class PurchaseItemCodec : ModelCodec<PurchaseItem, PurchaseItemCriteria> {

    private val productCodec = ProductCodec()

    override fun writeEntity(out: ExtensibleObjectOutput, entity: PurchaseItem, graph: EntityGraph) {
        out.beginObject()
        entity.id?.let { out.name("id").value(it) }
        if (graph.track(entity)) {
            entity.amount?.let { out.name("amount").value(it.toLong()) }
            entity.price?.let { out.name("price").value(it) }
            entity.product?.let {
                out.name("product")
                productCodec.writeEntity(out, it, graph)
            }
            entity.purchaseId?.let { out.name("purchaseId").value(it) }
        }
        out.endObject()
    }

    override fun writeEntityProjected(out: ExtensibleObjectOutput, entity: PurchaseItem, projection: PurchaseItem) {
        out.beginObject()
        entity.id?.let { out.name("id").value(it) }
        if (projection.amount != null) {
            out.name("amount")
            entity.amount?.let { out.value(it.toLong()) } ?: out.nullValue()
        }
        if (projection.price != null) {
            out.name("price")
            entity.price?.let { out.value(it) } ?: out.nullValue()
        }
        if (projection.product != null) {
            out.name("product")
            entity.product?.let { productCodec.writeEntity(out, it) } ?: out.nullValue()
        }
        if (projection.purchase != null) {
            out.name("purchaseId")
            entity.purchaseId?.let { out.value(it) } ?: out.nullValue()
        }
        out.endObject()
    }

    override fun computeProjection(newEntity: PurchaseItem, oldEntity: PurchaseItem): PurchaseItem {
        val pv = ProjectionValues
        val projection = PurchaseItem()
        if (newEntity.amount != oldEntity.amount) projection.amount = pv.i32
        if (newEntity.price != oldEntity.price) projection.price = pv.f64
        if (newEntity.productId != oldEntity.productId) projection.product = Product().apply { id = pv.i64 }
        if (newEntity.purchaseId != oldEntity.purchaseId) projection.purchase = Purchase().apply { id = pv.i64 }
        return projection
    }

    override fun readEntity(input: ExtensibleObjectInput): PurchaseItem {
        val item = PurchaseItem()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> item.id = InputCoerceUtils.asLong(input)
                "amount" -> item.amount = InputCoerceUtils.asInteger(input)
                "price" -> item.price = InputCoerceUtils.asDouble(input)
                "product" -> item.product = readProduct(input)
                "purchaseId" -> InputCoerceUtils.asLong(input)?.let { purchaseId -> item.purchase = Purchase().apply { id = purchaseId } }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return item
    }

    override fun readEntityForUpdate(input: ExtensibleObjectInput): ModelCodec.UpdateData<PurchaseItem> {
        val pv = ProjectionValues
        val entity = PurchaseItem()
        val projection = PurchaseItem()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> { entity.id = InputCoerceUtils.asLong(input); projection.id = pv.i64 }
                "amount" -> { entity.amount = InputCoerceUtils.asInteger(input); projection.amount = pv.i32 }
                "price" -> { entity.price = InputCoerceUtils.asDouble(input); projection.price = pv.f64 }
                "product" -> {
                    entity.product = readProduct(input)
                    projection.product = Product().apply { id = pv.i64 }
                }
                "purchaseId" -> {
                    InputCoerceUtils.asLong(input)?.let { purchaseId -> entity.purchase = Purchase().apply { id = purchaseId } }
                    projection.purchase = Purchase().apply { id = pv.i64 }
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return ModelCodec.UpdateData(entity, projection)
    }

    private fun readProduct(input: ExtensibleObjectInput): Product? =
        if (input.peek() == SerializationToken.NULL) input.nextNull() else productCodec.readEntity(input)

    override fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: PurchaseItemCriteria) {
        CriterionCodec.write(out, "purchaseItemId", criteria.purchaseItemId, CriterionCodec.LONG_OUT)
        CriterionCodec.write(out, "purchaseId", criteria.purchaseId, CriterionCodec.LONG_OUT)
        CriterionCodec.write(out, "productId", criteria.productId, CriterionCodec.LONG_OUT)
        CriterionCodec.write(out, "amount", criteria.amount, CriterionCodec.INT_OUT)
        CriterionCodec.write(out, "price", criteria.price, CriterionCodec.DOUBLE_OUT)
        CriterionCodec.write(out, "userId", criteria.userId, CriterionCodec.LONG_OUT)
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
    }

    override fun readCriteriaField(input: ExtensibleObjectInput, fieldName: String, criteria: PurchaseItemCriteria): Boolean {
        when (fieldName) {
            "purchaseItemId" -> CriterionCodec.read(input, criteria.purchaseItemId, CriterionCodec.LONG_IN)
            "purchaseId" -> CriterionCodec.read(input, criteria.purchaseId, CriterionCodec.LONG_IN)
            "productId" -> CriterionCodec.read(input, criteria.productId, CriterionCodec.LONG_IN)
            "amount" -> CriterionCodec.read(input, criteria.amount, CriterionCodec.INT_IN)
            "price" -> CriterionCodec.read(input, criteria.price, CriterionCodec.DOUBLE_IN)
            "userId" -> CriterionCodec.read(input, criteria.userId, CriterionCodec.LONG_IN)
            "orderBy" -> CriterionCodec.readOrderBy(input, PurchaseItemCriteria.OrderBy.entries)?.let { criteria.withOrderBy(it) }
            else -> return false
        }
        return true
    }

    override fun getProjection(criteria: PurchaseItemCriteria): PurchaseItem? = criteria.projection

    override fun setGeneratedId(entity: PurchaseItem, id: Long) {
        entity.id = id
    }
}
