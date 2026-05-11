package br.com.wdc.shopping.api.client

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository

class RestPurchaseItemRepository(private val config: RestConfig) : PurchaseItemRepository {

    override fun insert(purchaseItem: PurchaseItem): Boolean {
        val body = purchaseItem.toMap().toMutableMap()
        addPurchaseId(body, purchaseItem)
        val result = config.postJson("/api/repo/purchase-item/insert", body)
        val success = result.boolean("success")
        if (success && result.containsKey("id") && result["id"] != null) {
            purchaseItem.id = result.long("id")
        }
        return success
    }

    override fun insertOrUpdate(purchaseItem: PurchaseItem): Boolean {
        val body = purchaseItem.toMap().toMutableMap()
        addPurchaseId(body, purchaseItem)
        val result = config.postJson("/api/repo/purchase-item/upsert", body)
        val success = result.boolean("success")
        if (success && result.containsKey("id") && result["id"] != null) {
            purchaseItem.id = result.long("id")
        }
        return success
    }

    override fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem): Boolean {
        val newJson = newPurchaseItem.toMap().toMutableMap()
        addPurchaseId(newJson, newPurchaseItem)
        val oldJson = oldPurchaseItem.toMap().toMutableMap()
        addPurchaseId(oldJson, oldPurchaseItem)
        val body = mapOf(
            "newEntity" to newJson,
            "oldEntity" to oldJson
        )
        return config.postJson("/api/repo/purchase-item/update", body).boolean("success")
    }

    override fun delete(criteria: PurchaseItemCriteria): Int {
        return config.postJson("/api/repo/purchase-item/delete", buildCriteria(criteria)).int("count")
    }

    override fun count(criteria: PurchaseItemCriteria): Int {
        return config.postJson("/api/repo/purchase-item/count", buildCriteria(criteria)).int("count")
    }

    override fun fetch(criteria: PurchaseItemCriteria): List<PurchaseItem> {
        val body = buildCriteria(criteria).toMutableMap()
        criteria.projection?.let { body["projection"] = it.toMap() }
        val result = config.postJson("/api/repo/purchase-item/fetch", body)
        return result.list("items").map { it.toPurchaseItem() }
    }

    override fun fetchById(purchaseId: Long, projection: PurchaseItem?): PurchaseItem? {
        val body = mutableMapOf<String, Any?>("id" to purchaseId)
        projection?.let { body["projection"] = it.toMap() }
        val result = config.postJsonNullable("/api/repo/purchase-item/fetchById", body) ?: return null
        return result.toPurchaseItem()
    }

    private fun buildCriteria(criteria: PurchaseItemCriteria): Map<String, Any?> = buildMap {
        criteria.purchaseItemId?.let { put("purchaseItemId", it) }
        criteria.purchaseId?.let { put("purchaseId", it) }
        criteria.productId?.let { put("productId", it) }
        criteria.userId?.let { put("userId", it) }
        criteria.offset?.let { put("offset", it) }
        criteria.limit?.let { put("limit", it) }
        criteria.orderBy?.let { put("orderBy", it.name) }
    }

    private fun addPurchaseId(body: MutableMap<String, Any?>, item: PurchaseItem) {
        val purchaseId = item.purchase?.id
        if (purchaseId != null) {
            body["purchaseId"] = purchaseId
        }
    }
}
