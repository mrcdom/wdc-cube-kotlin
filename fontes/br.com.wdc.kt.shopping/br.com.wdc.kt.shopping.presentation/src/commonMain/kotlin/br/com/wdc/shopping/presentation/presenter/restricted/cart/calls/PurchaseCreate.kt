package br.com.wdc.shopping.presentation.presenter.restricted.cart.calls

import br.com.wdc.framework.domain.resource.ResourceCreate
import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.domain.exception.InvalidCartItemException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem
import kotlin.time.Clock

/**
 * The checkout: the purchase of [userId] with one item per cart entry, in one transaction (the purchase and its items
 * commit together, or nothing does). The items carry no price: the repository reads each product's in the INSERT.
 * Answers the purchase id.
 */
class PurchaseCreate(private val userId: Long, private val cartItems: List<CartItem>) :
    ResourceCreate<Long>({ ShoppingTransactions.BEAN.getOrNull() }) {

    private val purchaseRepo: PurchaseRepository get() = PurchaseRepository.BEAN.get()

    override suspend fun create(): Long {
        // built without apply: inside it, Purchase's own userId would shadow this call's
        val purchase = Purchase()
        purchase.user = User().also { it.id = userId }
        purchase.buyDate = Clock.System.now()
        purchase.items = cartItems.mapTo(ArrayList()) { item ->
            if (item.quantity < 1) throw InvalidCartItemException()
            PurchaseItem().also {
                it.product = Product().also { product -> product.id = item.id }
                it.amount = item.quantity
            }
        }
        check(purchaseRepo.insert(purchase)) { "Record not inserted" }
        return purchase.id!!
    }
}
