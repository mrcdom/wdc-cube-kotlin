package br.com.wdc.shopping.presentation.presenter.restricted.cart

import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.domain.exception.InvalidCartItemException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import kotlin.time.Clock

class CartManager(private val repo: PurchaseRepository) {

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
     * A snapshot of the cart: copies, not the cart's own items. The view state that holds it then changes only when
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

    suspend fun commit(subject: Subject): Long? {
        val purchaseId = doPurchase(subject.id!!, cart)
        clear()

        for (listener in ArrayList(changeListenerMap.values)) {
            listener()
        }

        for (listener in ArrayList(commitListenerMap.values)) {
            listener()
        }
        return purchaseId
    }

    private suspend fun doPurchase(userId: Long, request: List<CartItem>): Long? {
        val purchase = Purchase()
        purchase.user = User()
        purchase.user!!.id = userId
        purchase.buyDate = Clock.System.now()

        purchase.items = ArrayList()
        for (srcItem in request) {
            if (srcItem.quantity < 1) {
                throw InvalidCartItemException()
            }

            val purchaseItem = PurchaseItem()
            purchaseItem.product = Product()
            purchaseItem.product!!.id = srcItem.id
            purchaseItem.price = srcItem.price
            purchaseItem.amount = srcItem.quantity

            purchase.items!!.add(purchaseItem)
        }

        // The purchase and its items commit together, or nothing commits. Without a transaction service (tests
        // without persistence), runs directly.
        val transactions = ShoppingTransactions.BEAN.getOrNull()
        if (transactions == null) insert(purchase) else transactions.required { insert(purchase) }

        return purchase.id
    }

    private suspend fun insert(purchase: Purchase) {
        check(repo.insert(purchase)) { "Record not inserted" }
    }

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
