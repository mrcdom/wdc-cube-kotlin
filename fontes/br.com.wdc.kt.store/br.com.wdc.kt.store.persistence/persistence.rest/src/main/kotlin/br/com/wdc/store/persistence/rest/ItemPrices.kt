package br.com.wdc.store.persistence.rest

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository

/**
 * **The price of a purchase item is the server's.** On insert the repository reads it (right before the item repository's INSERT,
 * on every path); here, on an update through the API that touches the price or the product, the item gets its
 * product's current price. An item of a product that does not exist is refused (400).
 */
internal object ItemPrices {

    /**
     * On an update that writes the price or the product, sets the price to the current one of the product the item
     * will have (the new one, or the one it already has), and makes sure the price is written.
     */
    suspend fun applyTo(data: ModelCodec.UpdateData<PurchaseItem>) {
        val projection = data.projection
        if (projection.price == null && projection.product == null) return
        val productId = data.entity.productId?.takeIf { projection.product != null }
            ?: data.entity.id?.let { id ->
                PurchaseItemRepository.BEAN.get()
                    .fetchById(id, PurchaseItem().apply { this.id = ProjectionValues.i64; product = Product().apply { this.id = ProjectionValues.i64 } })
                    ?.productId
            }
        data.entity.price = currentPrice(productId)
        projection.price = ProjectionValues.f64
    }

    private suspend fun currentPrice(productId: Long?): Double {
        val id = productId ?: throw InvalidRequestException("purchase item without the product")
        val product = ProductRepository.BEAN.get()
            .fetchById(id, Product().apply { this.id = ProjectionValues.i64; price = ProjectionValues.f64 })
            ?: throw InvalidRequestException("unknown product: $id")
        return product.price ?: throw InvalidRequestException("product $id has no price")
    }
}
