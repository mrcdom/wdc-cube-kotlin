package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.ProductNotFoundException
import br.com.wdc.shopping.presentation.exception.WrongParametersException
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class ProductService(private val repo: ProductRepository) {

    fun loadProductById(productId: Long?): ProductInfo {
        if (productId == null) throw WrongParametersException()

        val product = repo.fetchById(productId, ProductInfo.projection())
            ?: throw ProductNotFoundException()
        return ProductInfo.create(product)!!
    }

    fun loadProductsWithoutDescription(limit: Int): List<ProductInfo> {
        val criteria = ProductCriteria()
            .withProjection(ProductInfo.projection())
            .withLimit(limit)

        criteria.projection!!.description = null

        return repo.fetch(criteria).mapNotNull { ProductInfo.create(it) }
    }
}
