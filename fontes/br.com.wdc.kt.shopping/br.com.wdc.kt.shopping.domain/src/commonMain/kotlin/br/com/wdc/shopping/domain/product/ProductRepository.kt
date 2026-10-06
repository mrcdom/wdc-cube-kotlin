package br.com.wdc.shopping.domain.product

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository

interface ProductRepository : Repository<Product, ProductCriteria, Long> {

    /** Todos os campos, menos a imagem. */
    override fun newProjection(): Product {
        val pv = ProjectionValues
        return Product().apply {
            id = pv.i64
            name = pv.str
            price = pv.f64
            description = pv.str
        }
    }

    override suspend fun fetchById(id: Long, projection: Product?): Product? =
        fetch(ProductCriteria().withProductId(id).withProjection(projection ?: newProjection()), 0, 1).firstOrNull()

    /** A imagem do produto, ou `null` se ele não tem. Produto inexistente é erro. */
    suspend fun fetchImage(productId: Long): ByteArray?

    suspend fun updateImage(productId: Long, image: ByteArray): Boolean

    companion object {
        val BEAN = AtomicRef<ProductRepository>()
    }
}
