package br.com.wdc.store.presentation.presenter.restricted.cart

import br.com.wdc.store.presentation.presenter.open.login.structs.Subject
import br.com.wdc.store.presentation.presenter.restricted.cart.calls.PurchaseCreate
import br.com.wdc.store.presentation.presenter.restricted.cart.structs.CartItem
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo

class CartManager {

    /**
     * What a cart keeps between page reloads: whose it is and how many of each product, in the cart's order. Written
     * as `userId|productId:quantity,productId:quantity`; names and prices are read again from the products.
     */
    data class Snapshot(val userId: Long, val quantities: Map<Long, Int>) {

        fun format(): String =
            "$userId|" + quantities.entries.joinToString(",") { (productId, quantity) -> "$productId:$quantity" }

        companion object {
            /** The snapshot written by [format], or `null` if the text is not one. */
            fun parse(text: String?): Snapshot? {
                if (text.isNullOrBlank()) return null
                val userId = text.substringBefore('|', "").toLongOrNull() ?: return null
                val items = text.substringAfter('|')
                val quantities = LinkedHashMap<Long, Int>()
                if (items.isNotEmpty()) {
                    for (item in items.split(',')) {
                        val productId = item.substringBefore(':', "").toLongOrNull() ?: return null
                        quantities[productId] = item.substringAfter(':').toIntOrNull() ?: return null
                    }
                }
                return Snapshot(userId, quantities)
            }
        }
    }

    private val cart: MutableList<CartItem> = ArrayList()

    private var listenerIdGen: Int = 0
    private val commitListenerMap: MutableMap<Int, suspend () -> Unit> = HashMap()
    private val changeListenerMap: MutableMap<Int, () -> Unit> = HashMap()

    fun addCommitListener(listener: suspend () -> Unit): () -> Unit {
        val listenerID = listenerIdGen++
        commitListenerMap[listenerID] = listener
        return { commitListenerMap.remove(listenerID) }
    }

    fun addChangeListener(listener: () -> Unit): () -> Unit {
        val listenerID = listenerIdGen++
        changeListenerMap[listenerID] = listener
        return { changeListenerMap.remove(listenerID) }
    }

    /**
     * A snapshot of the cart: copies, not the cart's own items. The scope that holds it then changes only when
     * it is assigned again — an observed field sees the new list, and the cart's later changes don't leak into a
     * list already painted.
     */
    fun getCartItems(): List<CartItem> = cart.map { it.copy() }

    fun addProduct(product: ProductInfo, quantity: Int) {
        var isNew = true
        for (item in cart) {
            if (item.id == product.id) {
                item.quantity += quantity
                isNew = false
                break
            }
        }

        if (isNew) {
            cart.add(CartItem.create(product, quantity))
        }
        // in both cases the cart changed: the header counter depends on this notice
        for (listener in ArrayList(changeListenerMap.values)) {
            listener()
        }
    }

    fun modifyProductQuantity(productId: Long, quantity: Int): Boolean {
        var found = false

        for (cartItem in cart) {
            if (cartItem.id == productId) {
                cartItem.quantity = quantity
                found = true
                break
            }
        }

        if (found) {
            for (listener in ArrayList(changeListenerMap.values)) {
                listener()
            }
        }

        return found
    }

    fun removeProduct(productId: Long): Boolean {
        var modified = false

        val it = cart.iterator()
        while (it.hasNext()) {
            val cartItem = it.next()
            if (cartItem.id == productId) {
                it.remove()
                modified = true
                break
            }
        }

        if (modified) {
            for (listener in ArrayList(changeListenerMap.values)) {
                listener()
            }
        }

        return modified
    }

    /** Buys what is in the cart ([PurchaseCreate]), empties it and tells the listeners; answers the purchase id. */
    suspend fun commit(subject: Subject): Long {
        val purchaseId = PurchaseCreate(subject.id!!, cart.map { it.copy() }).run()
        clear()

        for (listener in ArrayList(changeListenerMap.values)) {
            listener()
        }

        for (listener in ArrayList(commitListenerMap.values)) {
            listener()
        }
        return purchaseId
    }

    /** The cart's [Snapshot], for the given owner. */
    fun snapshot(userId: Long): Snapshot = Snapshot(userId, cart.associateTo(LinkedHashMap()) { it.id to it.quantity })

    fun getItemCount(): Int {
        var count = 0
        for (item in cart) {
            count += item.quantity
        }
        return count
    }

    fun getSize(): Int = cart.size

    fun clear() {
        cart.clear()
    }
}
