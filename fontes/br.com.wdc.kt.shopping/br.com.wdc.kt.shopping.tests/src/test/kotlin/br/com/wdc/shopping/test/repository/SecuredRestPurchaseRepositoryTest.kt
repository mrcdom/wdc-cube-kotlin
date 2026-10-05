package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Compra e item de compra pelo caminho REST com a segurança ligada: permissão, escopo (cada um só alcança as
 * próprias compras, na leitura e na escrita) e a senha do usuário da compra, que nunca sai do servidor.
 */
class SecuredRestPurchaseRepositoryTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-secured-purchase", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun purchases() = env.purchaseRepo

    private fun items() = env.purchaseItemRepo

    @AfterEach
    fun signOut() = env.logout()

    private fun assertHttp(status: Int, block: suspend () -> Unit) {
        val e = assertThrows(RuntimeException::class.java) { runBlocking { block() } }
        assertTrue(e.message!!.startsWith("HTTP $status"), "esperava HTTP $status, veio: ${e.message}")
    }

    private fun newItem(productId: Long, purchaseId: Long? = null) = PurchaseItem().apply {
        product = Product().apply { id = productId }
        amount = 1
        price = 10.5
        purchaseId?.let { purchase = Purchase().apply { id = it } }
    }

    /** Uma compra de fulano, com um item, feita por ele mesmo. */
    private suspend fun fulanoBuys(): Purchase {
        env.loginAs("fulano")
        val purchase = Purchase().apply {
            buyDate = Clock.System.now()
            items = mutableListOf(newItem(DBReset.PEN_DRIVE2GB_ID))
        }
        assertTrue(purchases().insert(purchase))
        return purchase
    }

    @Test
    fun withoutAuthentication_isRefused() {
        assertHttp(401) { purchases().fetch(PurchaseCriteria()) }
        assertHttp(401) { purchases().insert(Purchase().apply { buyDate = Clock.System.now() }) }
        assertHttp(401) { purchases().delete(PurchaseCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID)) }
        assertHttp(401) { items().fetch(PurchaseItemCriteria()) }
        assertHttp(401) { items().insert(newItem(DBReset.CAFETEIRA_ID, DBReset.ADMIN_FIRST_PURCHASE_ID)) }
    }

    @Test
    fun admin_seesEveryPurchase() = runBlocking {
        val fulanos = fulanoBuys()
        env.loginAs("admin")

        assertEquals(3, purchases().count(PurchaseCriteria()))
        assertEquals(4, items().count(PurchaseItemCriteria()))
        assertEquals(DBReset.FULANO_ID, purchases().fetchById(fulanos.id!!)!!.userId)
    }

    @Test
    fun customer_buysOnlyForThemselves() = runBlocking {
        env.loginAs("fulano")

        // pede a compra em nome de outro: fica no próprio nome
        val purchase = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.ADMIN_ID }
            items = mutableListOf(newItem(DBReset.CAFETEIRA_ID))
        }
        assertTrue(purchases().insert(purchase))
        assertEquals(DBReset.FULANO_ID, purchases().fetchById(purchase.id!!)!!.userId)

        env.loginAs("admin")
        assertEquals(2, purchases().count(PurchaseCriteria().withUserId(DBReset.ADMIN_ID)))
    }

    @Test
    fun customer_readsOnlyTheirOwnPurchases() = runBlocking {
        val own = fulanoBuys()

        // o escopo restringe ao próprio usuário, peça o que pedir
        assertEquals(listOf(own.id), purchases().fetch(PurchaseCriteria()).map { it.id })
        assertEquals(1, purchases().count(PurchaseCriteria()))
        assertEquals(emptyList<Purchase>(), purchases().fetch(PurchaseCriteria().withUserId(DBReset.ADMIN_ID)))
        assertEquals(emptyList<Purchase>(), purchases().fetch(PurchaseCriteria().withProductId(DBReset.CAFETEIRA_ID)))
        assertNull(purchases().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID))
        assertEquals(1, purchases().fetchPage(PurchaseCriteria(), 0, 10).totalItems)

        assertEquals(1, items().count(PurchaseItemCriteria()))
        assertEquals(listOf<Long?>(own.id), items().fetch(PurchaseItemCriteria()).map { it.purchaseId })
        assertEquals(emptyList<PurchaseItem>(), items().fetch(PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID)))
        assertNull(items().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID))
    }

    @Test
    fun customer_cannotWriteOnOtherUsersPurchases() = runBlocking {
        val own = fulanoBuys()
        val ownItem = items().fetch(PurchaseItemCriteria().withPurchaseId(own.id)).single()

        assertHttp(403) { items().insert(newItem(DBReset.PEN_DRIVE2GB_ID, DBReset.ADMIN_FIRST_PURCHASE_ID)) }
        assertHttp(403) { items().insert(newItem(DBReset.PEN_DRIVE2GB_ID)) }
        assertHttp(403) {
            items().update(PurchaseItem().apply { id = DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID; amount = 50 }, null, PurchaseItem().apply { amount = ProjectionValues.i32 })
        }
        // nem levar um item seu para a compra de outro
        assertHttp(403) {
            items().update(
                PurchaseItem().apply { id = ownItem.id; purchase = Purchase().apply { id = DBReset.ADMIN_FIRST_PURCHASE_ID } },
                null,
                PurchaseItem().apply { purchase = Purchase().apply { id = ProjectionValues.i64 } },
            )
        }
        assertHttp(403) {
            purchases().update(Purchase().apply { id = DBReset.ADMIN_FIRST_PURCHASE_ID; buyDate = Clock.System.now() }, null, Purchase().apply { buyDate = ProjectionValues.instant })
        }
        assertHttp(403) {
            purchases().update(Purchase().apply { id = own.id; user = User().apply { id = DBReset.ADMIN_ID } }, null, Purchase().apply { user = User().apply { id = ProjectionValues.i64 } })
        }
        // o cliente não apaga compras — nem as próprias
        assertHttp(403) { purchases().delete(PurchaseCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID)) }
        assertHttp(403) { items().delete(PurchaseItemCriteria().withPurchaseItemId(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)) }
        assertHttp(403) { purchases().delete(PurchaseCriteria().withPurchaseId(own.id)) }

        env.loginAs("admin")
        assertEquals(3, purchases().count(PurchaseCriteria()))
        assertEquals(4, items().count(PurchaseItemCriteria()))
        assertEquals(1, items().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)!!.amount)
        assertEquals(own.id, items().fetchById(ownItem.id!!)!!.purchaseId)
        // o administrador apaga, e a compra leva os itens
        assertEquals(1, purchases().delete(PurchaseCriteria().withPurchaseId(own.id)))
        assertEquals(3, items().count(PurchaseItemCriteria()))
    }

    @Test
    fun customer_writesOnTheirOwnPurchases() = runBlocking {
        val own = fulanoBuys()

        val extra = newItem(DBReset.CAFETEIRA_ID, own.id)
        assertTrue(items().insert(extra))
        assertTrue(items().update(PurchaseItem().apply { id = extra.id; amount = 3 }, null, PurchaseItem().apply { amount = ProjectionValues.i32 }))
        assertEquals(3, items().fetchById(extra.id!!)!!.amount)
        assertTrue(purchases().update(Purchase().apply { id = own.id; buyDate = Clock.System.now() }, null, Purchase().apply { buyDate = ProjectionValues.instant }))
        assertEquals(2, items().count(PurchaseItemCriteria()))
    }

    @Test
    fun rawResponses_neverCarryAPasswordKey() {
        env.loginAs("admin")
        val user = """"user":{"id":1,"userName":"~","password":"~"}"""
        val bodies = listOf(
            env.transport.postJson("/api/repo/purchase/fetch", """{"projection":{"id":1,$user}}"""),
            env.transport.postJson("/api/repo/purchase/fetch-page", """{"page":0,"pageSize":10,"projection":{"id":1,$user}}"""),
            env.transport.postJson("/api/repo/purchase/fetch-by-id", """{"id":${DBReset.ADMIN_FIRST_PURCHASE_ID},"projection":{"id":1,$user}}"""),
            env.transport.postJson("/api/repo/purchase-item/fetch", """{"projection":{"id":1,"purchaseId":1}}"""),
        )
        for (body in bodies) {
            assertTrue("admin" in body || "purchaseId" in body, body)
            assertFalse("password" in body, body)
        }
    }

    @Test
    fun unknownOrderBy_and_emptyDelete_are400() {
        env.loginAs("admin")
        assertHttp(400) { env.transport.postJson("/api/repo/purchase/fetch", """{"orderBy":"DESCENDING"}""") }
        assertHttp(400) { env.transport.postJson("/api/repo/purchase-item/fetch", """{"orderBy":"BY_MAGIC"}""") }
        assertHttp(400) { purchases().delete(PurchaseCriteria()) }
        assertHttp(400) { items().delete(PurchaseItemCriteria()) }
    }
}
