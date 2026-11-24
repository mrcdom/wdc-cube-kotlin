package br.com.wdc.store.test.transaction

import br.com.wdc.store.domain.StoreTransactions
import br.com.wdc.store.domain.exception.InvalidCartItemException
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.store.domain.user.User
import br.com.wdc.store.presentation.presenter.open.login.structs.Subject
import br.com.wdc.store.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.util.TestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** The checkout transaction boundary: the purchase and its items commit together, or nothing commits. */
class CheckoutTransactionTest {

    companion object {
        private val env = TestEnvironment("wedocode-store-checkout-tx")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)

        private const val NO_SUCH_PRODUCT = 987_654L
    }

    private fun product(id: Long, price: Double) = ProductInfo().apply { this.id = id; this.price = price; name = "p$id" }

    private fun fulano() = Subject().apply { id = DBReset.FULANO_ID }

    private suspend fun purchases() = env.purchaseRepo.count(PurchaseCriteria())

    private suspend fun items() = env.purchaseItemRepo.count(PurchaseItemCriteria())

    /** A cart whose second item is of a product that does not exist: the first is written, the second refused. */
    private fun cartThatFailsOnTheSecondItem() = CartManager().apply {
        addProduct(product(DBReset.COFFEE_MAKER_ID, 200.5), 1)
        addProduct(product(NO_SUCH_PRODUCT, 10.5), 2)
    }

    @Test
    fun addingMoreOfAProductAlreadyInTheCart_notifiesTheListeners() {
        val cart = CartManager()
        var changes = 0
        cart.addChangeListener { changes++ }
        cart.addProduct(product(DBReset.COFFEE_MAKER_ID, 200.5), 1)
        cart.addProduct(product(DBReset.COFFEE_MAKER_ID, 200.5), 2)
        assertEquals(2, changes, "the header counter depends on the notice in both cases")
        assertEquals(3, cart.getCartItems().single().quantity)
    }

    @Test
    fun checkout_withAZeroQuantity_isRefusedByTheCart_andWritesNothing() = runBlocking {
        val before = purchases()
        val cart = CartManager().apply {
            addProduct(product(DBReset.COFFEE_MAKER_ID, 200.5), 1)
            addProduct(product(DBReset.FLASH_DRIVE_2GB_ID, 16.25), 1)
            modifyProductQuantity(DBReset.FLASH_DRIVE_2GB_ID, 0)
        }
        assertThrows(InvalidCartItemException::class.java) { runBlocking { cart.commit(fulano()) } }
        assertEquals(before, purchases())
    }

    @Test
    fun checkout_commitsThePurchaseAndItsItems() = runBlocking {
        val cart = CartManager().apply {
            addProduct(product(DBReset.COFFEE_MAKER_ID, 200.5), 1)
            addProduct(product(DBReset.FLASH_DRIVE_2GB_ID, 16.25), 3)
        }
        val purchaseId = cart.commit(fulano())

        assertNotNull(purchaseId)
        assertEquals(DBReset.FULANO_ID, env.purchaseRepo.fetchById(purchaseId!!)!!.userId)
        assertEquals(2, env.purchaseItemRepo.count(PurchaseItemCriteria().withPurchaseId(purchaseId)))
        assertEquals(0, cart.getSize())
    }

    @Test
    fun failureInTheMiddleOfTheCheckout_leavesNoOrphanPurchase() = runBlocking {
        val cart = cartThatFailsOnTheSecondItem()

        assertThrows(Exception::class.java) { runBlocking { cart.commit(fulano()) } }

        assertEquals(2, purchases(), "the purchase was stored without its items")
        assertEquals(3, items(), "an item of a purchase that did not complete was left behind")
        assertEquals(2, cart.getSize(), "the cart was emptied without the purchase having happened")
    }

    @Test
    fun withoutTheTransactionBoundary_theSameFailureWouldLeaveAnOrphan() = runBlocking {
        // proof that the test above discriminates: without the transaction service, each write commits on its own
        val transactions = StoreTransactions.BEAN.get()
        StoreTransactions.BEAN.set(null)
        try {
            assertThrows(Exception::class.java) { runBlocking { cartThatFailsOnTheSecondItem().commit(fulano()) } }
        } finally {
            StoreTransactions.BEAN.set(transactions)
        }
        assertEquals(3, purchases())
        assertEquals(4, items())
    }

    @Test
    fun repositories_joinTheTransactionOfTheUseCase() = runBlocking {
        val purchase = Purchase().apply { buyDate = Clock.System.now(); user = User().apply { id = DBReset.FULANO_ID } }

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                StoreTransactions.BEAN.get().required {
                    assertTrue(env.purchaseRepo.insert(purchase))
                    assertTrue(
                        env.purchaseItemRepo.insert(
                            PurchaseItem().apply {
                                this.purchase = purchase
                                product = Product().apply { id = DBReset.COFFEE_MAKER_ID }
                                amount = 1
                                price = 200.5
                            }
                        )
                    )
                    // whoever is in the transaction sees what it wrote
                    assertEquals(3, purchases())
                    assertEquals(4, items())
                    error("desiste")
                }
            }
        }

        assertEquals(2, purchases())
        assertEquals(3, items())
    }

    @Test
    fun rollbackOnly_discardsTheWorkWithoutAnException() = runBlocking {
        StoreTransactions.BEAN.get().required { tx ->
            env.purchaseRepo.insert(Purchase().apply { buyDate = Clock.System.now(); user = User().apply { id = DBReset.FULANO_ID } })
            tx.setRollbackOnly()
        }
        assertEquals(2, purchases())
    }
}
