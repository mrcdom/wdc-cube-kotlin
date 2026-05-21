package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.PurchaseRepository

class RestPurchaseRepository(private val config: RestConfig) : PurchaseRepository {

    override suspend fun insert(purchase: Purchase): Boolean {
        val body = config.toJson { purchase.writeTo(it) }
        val input = config.postJson("/api/repo/purchase/insert", body)
        return readSuccessWithId(input, purchase)
    }

    override suspend fun insertOrUpdate(purchase: Purchase): Boolean {
        val body = config.toJson { purchase.writeTo(it) }
        val input = config.postJson("/api/repo/purchase/upsert", body)
        return readSuccessWithId(input, purchase)
    }

    override suspend fun update(newPurchase: Purchase, oldPurchase: Purchase): Boolean {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("newEntity"); newPurchase.writeTo(out)
            out.name("oldEntity"); oldPurchase.writeTo(out)
            out.endObject()
        }
        return readSuccess(config.postJson("/api/repo/purchase/update", body))
    }

    override suspend fun delete(criteria: PurchaseCriteria): Int {
        val body = config.toJson { writeCriteria(it, criteria) }
        return readCount(config.postJson("/api/repo/purchase/delete", body))
    }

    override suspend fun count(criteria: PurchaseCriteria): Int {
        val body = config.toJson { writeCriteria(it, criteria) }
        return readCount(config.postJson("/api/repo/purchase/count", body))
    }

    override suspend fun fetch(criteria: PurchaseCriteria): List<Purchase> {
        val body = config.toJson { out ->
            out.beginObject()
            writeCriteriaFields(out, criteria)
            criteria.projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJson("/api/repo/purchase/fetch", body)
        return readPurchaseList(input)
    }

    override suspend fun fetchPage(criteria: PurchaseCriteria): Page<Purchase> {
        val body = config.toJson { out ->
            out.beginObject()
            writeCriteriaFields(out, criteria)
            criteria.projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJson("/api/repo/purchase/fetchPage", body)
        return readPurchasePage(input)
    }

    override suspend fun fetchById(purchaseId: Long, projection: Purchase?): Purchase? {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("id").value(purchaseId)
            projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJsonNullable("/api/repo/purchase/fetchById", body) ?: return null
        return input.readPurchase()
    }

    private fun readSuccessWithId(input: ExtensibleObjectInput, purchase: Purchase): Boolean {
        var success = false
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "success" -> success = input.nextBoolean()
                "id" -> {
                    if (input.peek() != SerializationToken.NULL) {
                        purchase.id = input.nextLong()
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

    private fun writeCriteria(out: ExtensibleObjectOutput, criteria: PurchaseCriteria) {
        out.beginObject()
        writeCriteriaFields(out, criteria)
        out.endObject()
    }

    private fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: PurchaseCriteria) {
        criteria.purchaseId?.let { out.name("purchaseId").value(it) }
        criteria.userId?.let { out.name("userId").value(it) }
        criteria.offset?.let { out.name("offset").value(it.toLong()) }
        criteria.limit?.let { out.name("limit").value(it.toLong()) }
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
    }

    private fun readPurchaseList(input: ExtensibleObjectInput): List<Purchase> {
        val result = mutableListOf<Purchase>()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "items" -> {
                    input.beginArray()
                    while (input.hasNext()) { result.add(input.readPurchase()) }
                    input.endArray()
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return result
    }

    private fun readPurchasePage(input: ExtensibleObjectInput): Page<Purchase> {
        val items = mutableListOf<Purchase>()
        var totalCount = 0
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "items" -> {
                    input.beginArray()
                    while (input.hasNext()) { items.add(input.readPurchase()) }
                    input.endArray()
                }
                "totalCount" -> totalCount = input.nextInt()
                else -> input.skipValue()
            }
        }
        input.endObject()
        return Page(items, totalCount)
    }
}
