package br.com.wdc.shopping.persistence.rest.security

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.rest.policy.DataPolicy
import br.com.wdc.framework.rest.policy.EntityDescriptor
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.security.Role

/**
 * Who may do what with the products through the REST API.
 *
 * - **Every role** reads the catalog — every product, every field.
 * - **ADMIN** and **MANAGER** create, update and delete products, image included.
 *
 * The image is not in the JSON: it is read through its own public endpoint, and written through another one, which
 * checks it as an update of the `image` field.
 */
object ProductDataPolicy {

    private val PV = ProjectionValues

    val PRODUCT: EntityDescriptor<Product, ProductCriteria> = EntityDescriptor("product", ::Product, ::ProductCriteria) {
        projection(ProductCriteria::projection, ProductCriteria::withProjection)
        anyOf(ProductCriteria::anyOf, ProductCriteria::withAnyOf)
        key(Product::id, PV.i64)
        value(Product::name, PV.str)
        value(Product::price, PV.f64)
        value(Product::description, PV.str)
        value(Product::image, PV.bin)
        criterion("productId", Product::id)
        orderings { c ->
            when (c.orderBy) {
                null, ProductCriteria.OrderBy.OLDEST_FIRST, ProductCriteria.OrderBy.NEWEST_FIRST -> emptySet()
                ProductCriteria.OrderBy.NAME_A_TO_Z -> setOf(Product::name)
                ProductCriteria.OrderBy.CHEAPEST_FIRST, ProductCriteria.OrderBy.MOST_EXPENSIVE_FIRST -> setOf(Product::price)
            }
        }
        // the catalog fields: the image has its own endpoint, and does not travel in the JSON
        defaultShape { Product().apply { name = PV.str; price = PV.f64; description = PV.str } }
    }

    val POLICY: DataPolicy<Product, ProductCriteria> = DataPolicy(PRODUCT) {
        grant(Role.ADMIN.name, Role.MANAGER.name) {
            read(Product::name, Product::price, Product::description)
            create(Product::name, Product::price, Product::description, Product::image)
            update(Product::name, Product::price, Product::description, Product::image)
            delete()
        }
        grant(Role.CUSTOMER.name) {
            read(Product::name, Product::price, Product::description)
        }
    }
}
