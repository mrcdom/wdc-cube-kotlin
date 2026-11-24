package br.com.wdc.store.presentation.presenter.restricted.products.calls

import br.com.wdc.framework.domain.resource.ResourceQuery
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.presentation.exception.ProductNotFoundException
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo

/** One product, with its description; [ProductNotFoundException] if it does not exist. */
class ProductQuery(private val productId: Long) : ResourceQuery<ProductInfo>() {

    private val productRepo: ProductRepository get() = ProductRepository.BEAN.get()

    override suspend fun run(): ProductInfo {
        val product = productRepo.fetchById(productId, ProductInfo.projection())
            ?: throw ProductNotFoundException()
        return ProductInfo.create(product)!!
    }
}
