package br.com.wdc.shopping.api.client

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.repositories.ProductRepository

class RestProductRepository(private val config: RestConfig) : ProductRepository {

    override fun insert(product: Product): Boolean {
        val body = product.toMap().toMutableMap()
        val result = config.postJson("/api/repo/product/insert", body)
        val success = result.boolean("success")
        if (success && result.containsKey("id") && result["id"] != null) {
            product.id = result.long("id")
        }
        return success
    }

    override fun update(newProduct: Product, oldProduct: Product): Boolean {
        val body = mapOf(
            "newEntity" to newProduct.toMap(),
            "oldEntity" to oldProduct.toMap()
        )
        return config.postJson("/api/repo/product/update", body).boolean("success")
    }

    override fun insertOrUpdate(product: Product): Boolean {
        val body = product.toMap().toMutableMap()
        val result = config.postJson("/api/repo/product/upsert", body)
        val success = result.boolean("success")
        if (success && result.containsKey("id") && result["id"] != null) {
            product.id = result.long("id")
        }
        return success
    }

    override fun delete(criteria: ProductCriteria): Int {
        return config.postJson("/api/repo/product/delete", buildCriteria(criteria)).int("count")
    }

    override fun count(criteria: ProductCriteria): Int {
        return config.postJson("/api/repo/product/count", buildCriteria(criteria)).int("count")
    }

    override fun fetch(criteria: ProductCriteria): List<Product> {
        val body = buildCriteria(criteria).toMutableMap()
        criteria.projection?.let { body["projection"] = it.toMap() }
        val result = config.postJson("/api/repo/product/fetch", body)
        return result.list("items").map { it.toProduct() }
    }

    override fun fetchById(productId: Long, projection: Product?): Product? {
        val body = mutableMapOf<String, Any?>("id" to productId)
        projection?.let { body["projection"] = it.toMap() }
        val result = config.postJsonNullable("/api/repo/product/fetchById", body) ?: return null
        return result.toProduct()
    }

    override fun fetchImage(productId: Long): ByteArray? {
        return config.getBytes("/api/repo/product/$productId/image")
    }

    override fun updateImage(productId: Long, image: ByteArray): Boolean {
        return config.putBytes("/api/repo/product/$productId/image", image)
    }

    private fun buildCriteria(criteria: ProductCriteria): Map<String, Any?> = buildMap {
        criteria.productId?.let { put("productId", it) }
        criteria.offset?.let { put("offset", it) }
        criteria.limit?.let { put("limit", it) }
        criteria.orderBy?.let { put("orderBy", it.name) }
    }
}
