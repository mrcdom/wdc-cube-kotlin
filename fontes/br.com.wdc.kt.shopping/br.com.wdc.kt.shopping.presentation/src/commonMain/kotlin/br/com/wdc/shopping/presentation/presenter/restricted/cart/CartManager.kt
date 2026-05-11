package br.com.wdc.shopping.presentation.presenter.restricted.cart

import br.com.wdc.shopping.domain.exception.InvalidCartItemException
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import kotlinx.datetime.Clock

class CartManager(private val app: ShoppingApplication) {

    private val cart: MutableList<CartItem> = ArrayList()

    private var listenerIdGen: Int = 0
    private val commitListenerMap: MutableMap<Int, () -> Unit> = HashMap()
    private val changeListenerMap: MutableMap<Int, () -> Unit> = HashMap()

    fun addCommitListener(listener: () -> Unit): () -> Unit {
        val listenerID = listenerIdGen++
        commitListenerMap[listenerID] = listener
        return { commitListenerMap.remove(listenerID) }
    }

    fun addChangeListener(listener: () -> Unit): () -> Unit {
        val listenerID = listenerIdGen++
        changeListenerMap[listenerID] = listener
        return { changeListenerMap.remove(listenerID) }
    }

    fun getCartItems(): List<CartItem> = cart.toList()

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
            for (listener in ArrayList(changeListenerMap.values)) {
                listener()
            }
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

    fun commit(subject: Subject): Long? {
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

    private fun doPurchase(userId: Long, request: List<CartItem>): Long? {
        val purchase = Purchase()
        purchase.user = User()
        purchase.user!!.id = userId
        purchase.buyDate = Clock.System.now()

        purchase.items = ArrayList()
        for (srcItem in request) {
            if (srcItem.quantity < 0) {
                throw InvalidCartItemException()
            }

            val purchaseItem = PurchaseItem()
            purchaseItem.product = Product()
            purchaseItem.product!!.id = srcItem.id
            purchaseItem.price = srcItem.price
            purchaseItem.amount = srcItem.quantity

            purchase.items!!.add(purchaseItem)
        }

        check(app.getPurchaseRepository().insert(purchase)) { "Record not inserted" }

        return purchase.id
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
