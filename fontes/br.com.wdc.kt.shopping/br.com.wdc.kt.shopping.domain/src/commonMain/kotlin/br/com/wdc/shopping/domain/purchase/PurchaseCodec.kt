package br.com.wdc.shopping.domain.purchase

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.framework.domain.codec.EntityGraph
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.criteria.CriterionCodec
import br.com.wdc.framework.domain.projection.HasCriteria
import br.com.wdc.framework.domain.projection.HasSlice
import br.com.wdc.framework.domain.projection.ProjectionCollectionCodec
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.model.PlatformDateTime
import br.com.wdc.shopping.domain.product.ProductCodec
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCodec
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCodec

/**
 * Como [Purchase] e [PurchaseCriteria] trafegam — o mesmo codec no cliente e no servidor.
 *
 * Em `items`, a forma no fio distingue projeção de resultado: uma coleção de projeção que carrega critério ou
 * recorte vai como envelope (`{ shape, where?, limit?, offset? }`), e é assim que o filtro e a ordem dos itens
 * chegam ao servidor; o resultado é o array de sempre. A data trafega em ISO-8601.
 */
class PurchaseCodec : ModelCodec<Purchase, PurchaseCriteria> {

    private val userCodec = UserCodec()
    private val productCodec = ProductCodec()
    private val itemCodec = PurchaseItemCodec()

    override fun writeEntity(out: ExtensibleObjectOutput, entity: Purchase, graph: EntityGraph) {
        out.beginObject()
        entity.id?.let { out.name("id").value(it) }
        if (graph.track(entity)) {
            entity.buyDate?.let { out.name("buyDate").value(it.toString()) }
            entity.user?.let {
                out.name("user")
                userCodec.writeEntity(out, it, graph)
            }
            entity.items?.let { writeItems(out, it, graph) }
        }
        out.endObject()
    }

    private fun writeItems(out: ExtensibleObjectOutput, items: List<PurchaseItem>, graph: EntityGraph) {
        if (items is HasCriteria || items is HasSlice) {
            ProjectionCollectionCodec.write(
                out, "items", items,
                { o, item -> writeItem(o, item, graph) },
                { o, criteria ->
                    o.beginObject()
                    itemCodec.writeCriteriaFields(o, criteria as PurchaseItemCriteria)
                    o.endObject()
                },
            )
        } else {
            out.name("items").beginArray()
            items.forEach { writeItem(out, it, graph) }
            out.endArray()
        }
    }

    /** O item dentro da compra: sem a referência de volta, que é a própria compra. */
    private fun writeItem(out: ExtensibleObjectOutput, item: PurchaseItem, graph: EntityGraph) {
        out.beginObject()
        item.id?.let { out.name("id").value(it) }
        if (graph.track(item)) {
            item.amount?.let { out.name("amount").value(it.toLong()) }
            item.price?.let { out.name("price").value(it) }
            item.product?.let {
                out.name("product")
                productCodec.writeEntity(out, it, graph)
            }
        }
        out.endObject()
    }

    override fun writeEntityProjected(out: ExtensibleObjectOutput, entity: Purchase, projection: Purchase) {
        out.beginObject()
        entity.id?.let { out.name("id").value(it) }
        if (projection.buyDate != null) {
            out.name("buyDate")
            entity.buyDate?.let { out.value(it.toString()) } ?: out.nullValue()
        }
        if (projection.user != null) {
            out.name("user")
            entity.user?.let { userCodec.writeEntity(out, it) } ?: out.nullValue()
        }
        out.endObject()
    }

    override fun computeProjection(newEntity: Purchase, oldEntity: Purchase): Purchase {
        val pv = ProjectionValues
        val projection = Purchase()
        if (newEntity.buyDate != oldEntity.buyDate) projection.buyDate = pv.instant
        if (newEntity.userId != oldEntity.userId) projection.user = User().apply { id = pv.i64 }
        return projection
    }

    override fun readEntity(input: ExtensibleObjectInput): Purchase {
        val purchase = Purchase()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> purchase.id = InputCoerceUtils.asLong(input)
                "buyDate" -> purchase.buyDate = readInstant(input)
                "user" -> purchase.user = readNullable(input) { userCodec.readEntity(it) }
                "items" -> purchase.items = readItems(input, purchase)
                else -> input.skipValue()
            }
        }
        input.endObject()
        return purchase
    }

    private fun readItems(input: ExtensibleObjectInput, parent: Purchase): MutableList<PurchaseItem>? = when {
        input.peek() == SerializationToken.NULL -> input.nextNull()
        ProjectionCollectionCodec.isProjectionEnvelope(input) ->
            ProjectionCollectionCodec.read(input, { readItem(it, parent) }, ::readItemCriteria)
        else -> {
            val list = ArrayList<PurchaseItem>()
            input.beginArray()
            while (input.hasNext()) {
                list.add(readItem(input, parent))
            }
            input.endArray()
            list
        }
    }

    /** Cada item recebe a referência de volta como stub — só a chave da compra. */
    private fun readItem(input: ExtensibleObjectInput, parent: Purchase): PurchaseItem {
        val item = PurchaseItem()
        parent.id?.let { parentId -> item.purchase = Purchase().apply { id = parentId } }
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> item.id = InputCoerceUtils.asLong(input)
                "amount" -> item.amount = InputCoerceUtils.asInteger(input)
                "price" -> item.price = InputCoerceUtils.asDouble(input)
                "product" -> item.product = readNullable(input) { productCodec.readEntity(it) }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return item
    }

    private fun readItemCriteria(input: ExtensibleObjectInput): PurchaseItemCriteria {
        val criteria = PurchaseItemCriteria()
        input.beginObject()
        while (input.hasNext()) {
            val name = input.nextName()
            if (!itemCodec.readCriteriaField(input, name, criteria)) {
                input.skipValue()
            }
        }
        input.endObject()
        return criteria
    }

    override fun readEntityForUpdate(input: ExtensibleObjectInput): ModelCodec.UpdateData<Purchase> {
        val pv = ProjectionValues
        val entity = Purchase()
        val projection = Purchase()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> { entity.id = InputCoerceUtils.asLong(input); projection.id = pv.i64 }
                "buyDate" -> { entity.buyDate = readInstant(input); projection.buyDate = pv.instant }
                "user" -> {
                    entity.user = readNullable(input) { userCodec.readEntity(it) }
                    projection.user = User().apply { id = pv.i64 }
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return ModelCodec.UpdateData(entity, projection)
    }

    override fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: PurchaseCriteria) {
        CriterionCodec.write(out, "purchaseId", criteria.purchaseId, CriterionCodec.LONG_OUT)
        CriterionCodec.write(out, "userId", criteria.userId, CriterionCodec.LONG_OUT)
        CriterionCodec.write(out, "buyDate", criteria.buyDate, CriterionCodec.INSTANT_OUT)
        CriterionCodec.write(out, "productId", criteria.productId, CriterionCodec.LONG_OUT)
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
    }

    override fun readCriteriaField(input: ExtensibleObjectInput, fieldName: String, criteria: PurchaseCriteria): Boolean {
        when (fieldName) {
            "purchaseId" -> CriterionCodec.read(input, criteria.purchaseId, CriterionCodec.LONG_IN)
            "userId" -> CriterionCodec.read(input, criteria.userId, CriterionCodec.LONG_IN)
            "buyDate" -> CriterionCodec.read(input, criteria.buyDate, CriterionCodec.INSTANT_IN)
            "productId" -> CriterionCodec.read(input, criteria.productId, CriterionCodec.LONG_IN)
            "orderBy" -> CriterionCodec.readOrderBy(input, PurchaseCriteria.OrderBy.entries)?.let { criteria.withOrderBy(it) }
            else -> return false
        }
        return true
    }

    override fun getProjection(criteria: PurchaseCriteria): Purchase? = criteria.projection

    override fun setGeneratedId(entity: Purchase, id: Long) {
        entity.id = id
    }

    private fun readInstant(input: ExtensibleObjectInput): PlatformDateTime? =
        InputCoerceUtils.asString(input)?.let { PlatformDateTime.parse(it) }

    private inline fun <T> readNullable(input: ExtensibleObjectInput, read: (ExtensibleObjectInput) -> T): T? =
        if (input.peek() == SerializationToken.NULL) input.nextNull() else read(input)
}
