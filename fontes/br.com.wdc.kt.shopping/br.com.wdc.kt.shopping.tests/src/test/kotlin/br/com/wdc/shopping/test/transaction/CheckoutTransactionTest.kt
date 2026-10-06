package br.com.wdc.shopping.test.transaction

import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** A fronteira de transação do checkout: a compra e os seus itens confirmam juntos, ou nada confirma. */
class CheckoutTransactionTest {

    companion object {
        private val env = TestEnvironment("wedocode-shopping-checkout-tx")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)

        private const val NO_SUCH_PRODUCT = 987_654L
    }

    private fun product(id: Long, price: Double) = ProductInfo().apply { this.id = id; this.price = price; name = "p$id" }

    private fun fulano() = Subject().apply { id = DBReset.FULANO_ID }

    private suspend fun purchases() = env.purchaseRepo.count(PurchaseCriteria())

    private suspend fun items() = env.purchaseItemRepo.count(PurchaseItemCriteria())

    /** Um carrinho cujo segundo item viola a chave estrangeira do produto: o primeiro grava, o segundo falha. */
    private fun cartThatFailsOnTheSecondItem() = CartManager(env.purchaseRepo).apply {
        addProduct(product(DBReset.CAFETEIRA_ID, 200.5), 1)
        addProduct(product(NO_SUCH_PRODUCT, 10.5), 2)
    }

    @Test
    fun checkout_commitsThePurchaseAndItsItems() = runBlocking {
        val cart = CartManager(env.purchaseRepo).apply {
            addProduct(product(DBReset.CAFETEIRA_ID, 200.5), 1)
            addProduct(product(DBReset.PEN_DRIVE2GB_ID, 16.25), 3)
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

        assertEquals(2, purchases(), "a compra ficou gravada sem os itens")
        assertEquals(3, items(), "ficou item de uma compra que não se completou")
        assertEquals(2, cart.getSize(), "o carrinho foi esvaziado sem a compra ter acontecido")
    }

    @Test
    fun withoutTheTransactionBoundary_theSameFailureWouldLeaveAnOrphan() = runBlocking {
        // prova de que o teste acima discrimina: sem o serviço de transação, cada escrita confirma sozinha
        val transactions = ShoppingTransactions.BEAN.get()
        ShoppingTransactions.BEAN.set(null)
        try {
            assertThrows(Exception::class.java) { runBlocking { cartThatFailsOnTheSecondItem().commit(fulano()) } }
        } finally {
            ShoppingTransactions.BEAN.set(transactions)
        }
        assertEquals(3, purchases())
        assertEquals(4, items())
    }

    @Test
    fun repositories_joinTheTransactionOfTheUseCase() = runBlocking {
        val purchase = Purchase().apply { buyDate = Clock.System.now(); user = User().apply { id = DBReset.FULANO_ID } }

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                ShoppingTransactions.BEAN.get().required {
                    assertTrue(env.purchaseRepo.insert(purchase))
                    assertTrue(
                        env.purchaseItemRepo.insert(
                            PurchaseItem().apply {
                                this.purchase = purchase
                                product = Product().apply { id = DBReset.CAFETEIRA_ID }
                                amount = 1
                                price = 200.5
                            }
                        )
                    )
                    // quem está na transação vê o que ela gravou
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
        ShoppingTransactions.BEAN.get().required { tx ->
            env.purchaseRepo.insert(Purchase().apply { buyDate = Clock.System.now(); user = User().apply { id = DBReset.FULANO_ID } })
            tx.setRollbackOnly()
        }
        assertEquals(2, purchases())
    }
}
