package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCodec
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository

class HttpProductRepository(
    transport: HttpTransport,
    codec: ModelCodec<Product, ProductCriteria> = ProductCodec(),
) : HttpRepository<Product, ProductCriteria, Long>(transport, codec, "/api/repo/product"), ProductRepository {

    override suspend fun fetchImage(productId: Long): ByteArray? = transport.getBytes("$basePath/$productId/image")

    override suspend fun updateImage(productId: Long, image: ByteArray): Boolean =
        transport.putBytes("$basePath/$productId/image", image)

    override fun fill(target: Product, stored: Product) = fillProduct(target, stored)
}

internal fun fillProduct(target: Product, stored: Product) {
    stored.id?.let { target.id = it }
    stored.name?.let { target.name = it }
    stored.price?.let { target.price = it }
    stored.description?.let { target.description = it }
    stored.image?.let { target.image = it }
}
