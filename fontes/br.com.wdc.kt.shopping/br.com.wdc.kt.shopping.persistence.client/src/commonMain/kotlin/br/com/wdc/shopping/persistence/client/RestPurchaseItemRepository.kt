package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository

class RestPurchaseItemRepository(private val config: RestConfig) : PurchaseItemRepository {

    override fun insert(purchaseItem: PurchaseItem): Boolean {
        val body = config.toJson { out -> writeItemWithPurchaseId(out, purchaseItem) }
        val input = config.postJson("/api/repo/purchase-item/insert", body)
        return readSuccessWithId(input, purchaseItem)
    }

    override fun insertOrUpdate(purchaseItem: PurchaseItem): Boolean {
        val body = config.toJson { out -> writeItemWithPurchaseId(out, purchaseItem) }
        val input = config.postJson("/api/repo/purchase-item/upsert", body)
        return readSuccessWithId(input, purchaseItem)
    }

    override fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem): Boolean {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("newEntity"); writeItemWithPurchaseId(out, newPurchaseItem)
            out.name("oldEntity"); writeItemWithPurchaseId(out, oldPurchaseItem)
            out.endObject()
        }
        return readSuccess(config.postJson("/api/repo/purchase-item/update", body))
    }

    override fun delete(criteria: PurchaseItemCriteria): Int {
        val body = config.toJson { writeCriteria(it, criteria) }
        return readCount(config.postJson("/api/repo/purchase-item/delete", body))
    }

    override fun count(criteria: PurchaseItemCriteria): Int {
        val body = config.toJson { writeCriteria(it, criteria) }
        return readCount(config.postJson("/api/repo/purchase-item/count", body))
    }

    override fun fetch(criteria: PurchaseItemCriteria): List<PurchaseItem> {
        val body = config.toJson { out ->
            out.beginObject()
            writeCriteriaFields(out, criteria)
            criteria.projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJson("/api/repo/purchase-item/fetch", body)
        return readPurchaseItemList(input)
    }

    override fun fetchById(purchaseId: Long, projection: PurchaseItem?): PurchaseItem? {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("id").value(purchaseId)
            projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJsonNullable("/api/repo/purchase-item/fetchById", body) ?: return null
        return input.readPurchaseItem()
    }

    private fun writeItemWithPurchaseId(out: ExtensibleObjectOutput, item: PurchaseItem) {
        out.beginObject()
        item.id?.let { out.name("id").value(it) }
        item.amount?.let { out.name("amount").value(it.toLong()) }
        item.price?.let { out.name("price").value(it) }
        item.product?.let { out.name("product"); it.writeTo(out) }
        item.purchase?.id?.let { out.name("purchaseId").value(it) }
        out.endObject()
    }

    private fun readSuccessWithId(input: ExtensibleObjectInput, item: PurchaseItem): Boolean {
        var success = false
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "success" -> success = input.nextBoolean()
                "id" -> {
                    if (input.peek() != SerializationToken.NULL) {
                        item.id = input.nextLong()
                    } else {
                        input.nextNull<Any>()
                    }
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return success
    }

    private fun writeCriteria(out: ExtensibleObjectOutput, criteria: PurchaseItemCriteria) {
        out.beginObject()
        writeCriteriaFields(out, criteria)
        out.endObject()
    }

    private fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: PurchaseItemCriteria) {
        criteria.purchaseItemId?.let { out.name("purchaseItemId").value(it) }
        criteria.purchaseId?.let { out.name("purchaseId").value(it) }
        criteria.productId?.let { out.name("productId").value(it) }
        criteria.userId?.let { out.name("userId").value(it) }
        criteria.offset?.let { out.name("offset").value(it.toLong()) }
        criteria.limit?.let { out.name("limit").value(it.toLong()) }
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
    }

    private fun readPurchaseItemList(input: ExtensibleObjectInput): List<PurchaseItem> {
        val result = mutableListOf<PurchaseItem>()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "items" -> {
                    input.beginArray()
                    while (input.hasNext()) { result.add(input.readPurchaseItem()) }
                    input.endArray()
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return result
    }
}
