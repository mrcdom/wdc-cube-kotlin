package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.ProductNotFoundException
import br.com.wdc.shopping.presentation.exception.WrongParametersException
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class ProductService(private val repo: ProductRepository) {

    suspend fun loadProductById(productId: Long?): ProductInfo {
        if (productId == null) throw WrongParametersException()

        val product = repo.fetchById(productId, ProductInfo.projection())
            ?: throw ProductNotFoundException()
        return ProductInfo.create(product)!!
    }

    suspend fun loadProductsWithoutDescription(limit: Int): List<ProductInfo> {
        val projection = ProductInfo.projection().apply { description = null }
        val criteria = ProductCriteria().withProjection(projection)
        return repo.fetch(criteria, limit = limit).mapNotNull { ProductInfo.create(it) }
    }
}
