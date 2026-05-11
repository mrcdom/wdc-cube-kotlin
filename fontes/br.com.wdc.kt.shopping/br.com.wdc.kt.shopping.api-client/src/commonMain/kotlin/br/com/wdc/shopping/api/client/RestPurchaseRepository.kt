package br.com.wdc.shopping.api.client

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.repositories.PurchaseRepository

class RestPurchaseRepository(private val config: RestConfig) : PurchaseRepository {

    override fun insert(purchase: Purchase): Boolean {
        val body = purchase.toMap().toMutableMap()
        val result = config.postJson("/api/repo/purchase/insert", body)
        val success = result.boolean("success")
        if (success && result.containsKey("id") && result["id"] != null) {
            purchase.id = result.long("id")
        }
        return success
    }

    override fun insertOrUpdate(purchase: Purchase): Boolean {
        val body = purchase.toMap().toMutableMap()
        val result = config.postJson("/api/repo/purchase/upsert", body)
        val success = result.boolean("success")
        if (success && result.containsKey("id") && result["id"] != null) {
            purchase.id = result.long("id")
        }
        return success
    }

    override fun update(newPurchase: Purchase, oldPurchase: Purchase): Boolean {
        val body = mapOf(
            "newEntity" to newPurchase.toMap(),
            "oldEntity" to oldPurchase.toMap()
        )
        return config.postJson("/api/repo/purchase/update", body).boolean("success")
    }

    override fun delete(criteria: PurchaseCriteria): Int {
        return config.postJson("/api/repo/purchase/delete", buildCriteria(criteria)).int("count")
    }

    override fun count(criteria: PurchaseCriteria): Int {
        return config.postJson("/api/repo/purchase/count", buildCriteria(criteria)).int("count")
    }

    override fun fetch(criteria: PurchaseCriteria): List<Purchase> {
        val body = buildCriteria(criteria).toMutableMap()
        criteria.projection?.let { body["projection"] = it.toMap() }
        val result = config.postJson("/api/repo/purchase/fetch", body)
        return result.list("items").map { it.toPurchase() }
    }

    override fun fetchById(purchaseId: Long, projection: Purchase?): Purchase? {
        val body = mutableMapOf<String, Any?>("id" to purchaseId)
        projection?.let { body["projection"] = it.toMap() }
        val result = config.postJsonNullable("/api/repo/purchase/fetchById", body) ?: return null
        return result.toPurchase()
    }

    private fun buildCriteria(criteria: PurchaseCriteria): Map<String, Any?> = buildMap {
        criteria.purchaseId?.let { put("purchaseId", it) }
        criteria.userId?.let { put("userId", it) }
        criteria.offset?.let { put("offset", it) }
        criteria.limit?.let { put("limit", it) }
        criteria.orderBy?.let { put("orderBy", it.name) }
    }
}
