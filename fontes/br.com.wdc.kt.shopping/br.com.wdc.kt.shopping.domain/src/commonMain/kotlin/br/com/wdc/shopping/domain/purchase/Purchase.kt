package br.com.wdc.shopping.domain.purchase

import br.com.wdc.framework.domain.codec.KeyedEntity
import br.com.wdc.shopping.domain.model.PlatformDateTime
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.user.User

/**
 * Compra de um usuário, com os seus itens.
 *
 * Os campos são anuláveis porque `null` tem dois papéis: dado ausente e, numa projeção, "não traga este campo".
 * Em [items], a projeção é uma coleção com **um** elemento — a forma do item — e pode carregar critério, ordem
 * e recorte (`ProjectionValues.singletonList`).
 */
class Purchase : KeyedEntity {
    var id: Long? = null
    var buyDate: PlatformDateTime? = null
    var user: User? = null
    var items: MutableList<PurchaseItem>? = null

    val userId: Long? get() = user?.id

    override fun key(): Any? = id
}
