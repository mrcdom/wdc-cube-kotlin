package br.com.wdc.shopping.persistence.repository.product

import br.com.wdc.framework.commons.util.TransactionContext
import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.persistence.repository.BaseRepository

class ProductRepositoryImpl : BaseRepository(), ProductRepository {

    override fun insert(product: Product): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            InsertProductRowCmd.run(tx.connection(), product)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun insertOrUpdate(product: Product): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            if (product.id == null) InsertProductRowCmd.run(tx.connection(), product)
            else UpdateProductRowCmd.run(tx.connection(), product)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun update(newProduct: Product, oldProduct: Product): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            UpdateProductRowCmd.run(tx.connection(), newProduct, oldProduct)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun delete(criteria: ProductCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            DeleteProductsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun count(criteria: ProductCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            CountProductsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetch(criteria: ProductCriteria): List<Product> = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchProductsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetchPage(criteria: ProductCriteria): Page<Product> = try {
        TransactionContext.begin(dataSource()).use { tx ->
            val totalCount = CountProductsCmd.byCriteria(tx.connection(), criteria)
            val items = FetchProductsCmd.byCriteria(tx.connection(), criteria)
            Page(items, totalCount)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetchById(productId: Long, projection: Product?): Product? = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchProductsCmd.byId(tx.connection(), productId, projection)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetchImage(productId: Long): ByteArray? = try {
        TransactionContext.begin(dataSource()).use { tx ->
            val projection = Product()
            projection.image = ProjectionValues.bin
            FetchProductsCmd.byId(tx.connection(), productId, projection)!!.image
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun updateImage(productId: Long, image: ByteArray): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            val product = Product()
            product.id = productId
            product.image = image
            UpdateProductRowCmd.run(tx.connection(), product)
        }
    } catch (e: Exception) {
        readException(e)
    }
}
