package br.com.wdc.store.domain.product

import br.com.wdc.framework.domain.codec.KeyedEntity

/**
 * Catalog product.
 *
 * The fields are nullable because `null` has two roles: missing data and, in a projection, "do not fetch this field".
 */
class Product : KeyedEntity {
    var id: Long? = null
    var name: String? = null
    var price: Double? = null
    var description: String? = null

    /** Does not travel with the entity: it has its own endpoint and repository methods. */
    var image: ByteArray? = null

    override fun key(): Any? = id
}
