package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.repositories.ProductRepository

class RestProductRepository(private val config: RestConfig) : ProductRepository {

    override fun insert(product: Product): Boolean {
        val body = config.toJson { product.writeTo(it) }
        val input = config.postJson("/api/repo/product/insert", body)
        return readSuccessWithId(input, product)
    }

    override fun update(newProduct: Product, oldProduct: Product): Boolean {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("newEntity"); newProduct.writeTo(out)
            out.name("oldEntity"); oldProduct.writeTo(out)
            out.endObject()
        }
        return readSuccess(config.postJson("/api/repo/product/update", body))
    }

    override fun insertOrUpdate(product: Product): Boolean {
        val body = config.toJson { product.writeTo(it) }
        val input = config.postJson("/api/repo/product/upsert", body)
        return readSuccessWithId(input, product)
    }

    override fun delete(criteria: ProductCriteria): Int {
        val body = config.toJson { writeCriteria(it, criteria) }
        return readCount(config.postJson("/api/repo/product/delete", body))
    }

    override fun count(criteria: ProductCriteria): Int {
        val body = config.toJson { writeCriteria(it, criteria) }
        return readCount(config.postJson("/api/repo/product/count", body))
    }

    override fun fetch(criteria: ProductCriteria): List<Product> {
        val body = config.toJson { out ->
            out.beginObject()
            writeCriteriaFields(out, criteria)
            criteria.projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJson("/api/repo/product/fetch", body)
        return readProductList(input)
    }

    override fun fetchById(productId: Long, projection: Product?): Product? {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("id").value(productId)
            projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJsonNullable("/api/repo/product/fetchById", body) ?: return null
        return input.readProduct()
    }

    override fun fetchImage(productId: Long): ByteArray? {
        return config.getBytes("/api/repo/product/$productId/image")
    }

    override fun updateImage(productId: Long, image: ByteArray): Boolean {
        return config.putBytes("/api/repo/product/$productId/image", image)
    }

    private fun readSuccessWithId(input: ExtensibleObjectInput, product: Product): Boolean {
        var success = false
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "success" -> success = input.nextBoolean()
                "id" -> {
                    if (input.peek() != SerializationToken.NULL) {
                        product.id = input.nextLong()
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

    private fun writeCriteria(out: ExtensibleObjectOutput, criteria: ProductCriteria) {
        out.beginObject()
        writeCriteriaFields(out, criteria)
        out.endObject()
    }

    private fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: ProductCriteria) {
        criteria.productId?.let { out.name("productId").value(it) }
        criteria.offset?.let { out.name("offset").value(it.toLong()) }
        criteria.limit?.let { out.name("limit").value(it.toLong()) }
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
    }

    private fun readProductList(input: ExtensibleObjectInput): List<Product> {
        val result = mutableListOf<Product>()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "items" -> {
                    input.beginArray()
                    while (input.hasNext()) { result.add(input.readProduct()) }
                    input.endArray()
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return result
    }
}
