package br.com.wdc.shopping.domain.product

import br.com.wdc.framework.domain.codec.KeyedEntity

/**
 * Produto do catálogo.
 *
 * Os campos são anuláveis porque `null` tem dois papéis: dado ausente e, numa projeção, "não traga este campo".
 */
class Product : KeyedEntity {
    var id: Long? = null
    var name: String? = null
    var price: Double? = null
    var description: String? = null

    /** Não trafega com a entidade: tem endpoint e métodos próprios no repositório. */
    var image: ByteArray? = null

    override fun key(): Any? = id
}
