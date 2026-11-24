package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria
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
 * Purchase and purchase item over the REST path with security on, by their data policies: a purchase is a receipt —
 * its buyer creates and reads it, only an administrator changes it —, each one only reaches their own purchases, and
 * the password of the purchase's user never leaves the server.
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

    /** An item as a customer sends it: no price — the repository reads the product's in the INSERT. */
    private fun newItem(productId: Long, purchaseId: Long? = null) = PurchaseItem().apply {
        product = Product().apply { id = productId }
        amount = 1
        purchaseId?.let { purchase = Purchase().apply { id = it } }
    }

    /** A purchase by fulano, with one item, made by himself. */
    private suspend fun fulanoBuys(): Purchase {
        env.loginAs("fulano")
        val purchase = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.FULANO_ID }
            items = mutableListOf(newItem(DBReset.FLASH_DRIVE_2GB_ID))
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
        assertHttp(401) { items().insert(newItem(DBReset.COFFEE_MAKER_ID, DBReset.ADMIN_FIRST_PURCHASE_ID)) }
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

        // on someone else's behalf, or on no one's: refused
        val forAdmin = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.ADMIN_ID }
            items = mutableListOf(newItem(DBReset.COFFEE_MAKER_ID))
        }
        assertHttp(403) { purchases().insert(forAdmin) }
        assertHttp(403) { purchases().insert(Purchase().apply { buyDate = Clock.System.now() }) }
        // the price is the server's: a customer does not send it
        val priced = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.FULANO_ID }
            items = mutableListOf(newItem(DBReset.COFFEE_MAKER_ID).apply { price = 0.01 })
        }
        val e = assertThrows(RuntimeException::class.java) { runBlocking { purchases().insert(priced) } }
        assertTrue(e.message!!.startsWith("HTTP 403") && "[price]" in e.message!!, e.message)
        // the buyer is written by its key only
        val renamed = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.FULANO_ID; name = "Someone Else" }
        }
        assertHttp(403) { purchases().insert(renamed) }

        val own = fulanoBuys()
        assertEquals(DBReset.FULANO_ID, purchases().fetchById(own.id!!)!!.userId)

        env.loginAs("admin")
        assertEquals(2, purchases().count(PurchaseCriteria().withUserId(DBReset.ADMIN_ID)))
        assertEquals(1, purchases().count(PurchaseCriteria().withUserId(DBReset.FULANO_ID)))
    }

    @Test
    fun customer_readsOnlyTheirOwnPurchases() = runBlocking {
        val own = fulanoBuys()

        // the scope restricts to the user themselves, whatever is asked
        assertEquals(listOf(own.id), purchases().fetch(PurchaseCriteria()).map { it.id })
        assertEquals(1, purchases().count(PurchaseCriteria()))
        assertEquals(emptyList<Purchase>(), purchases().fetch(PurchaseCriteria().withUserId(DBReset.ADMIN_ID)))
        assertEquals(emptyList<Purchase>(), purchases().fetch(PurchaseCriteria().withProductId(DBReset.COFFEE_MAKER_ID)))
        assertNull(purchases().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID))
        assertEquals(1, purchases().fetchPage(PurchaseCriteria(), 0, 10).totalItems)

        assertEquals(1, items().count(PurchaseItemCriteria()))
        val withPurchase = PurchaseItem().apply { id = ProjectionValues.i64; purchase = Purchase().apply { id = ProjectionValues.i64 } }
        assertEquals(listOf<Long?>(own.id), items().fetch(PurchaseItemCriteria().withProjection(withPurchase)).map { it.purchaseId })
        assertEquals(emptyList<PurchaseItem>(), items().fetch(PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID)))
        assertNull(items().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID))
    }

    @Test
    fun customer_disjunctiveFilterOnTheOwnerField_doesNotWidenTheScope() = runBlocking {
        val own = fulanoBuys()

        // "or" on the owner field: the scope added to it used to become USERID IS NOT NULL OR USERID = owner
        val anyone = PurchaseCriteria().also { it.userId.or().isNotNull() }
        assertEquals(listOf(own.id), purchases().fetch(anyone).map { it.id })
        assertEquals(1, purchases().count(PurchaseCriteria().also { it.userId.or().isNotNull() }))
        assertEquals(emptyList<Purchase>(), purchases().fetch(PurchaseCriteria().also { it.userId.or().eq(DBReset.ADMIN_ID) }))
        assertEquals(1, items().count(PurchaseItemCriteria().also { it.userId.or().isNotNull() }))
        assertEquals(1, env.userRepo.count(UserCriteria().also { it.userId.or().isNotNull() }))

        // the same raw request, as any client would send it
        val raw = env.transport.postJson(
            "/api/repo/purchase/count", """{"userId":{"or":true,"p":[{"o":"IS_NOT_NULL"}]}}""",
        )
        assertEquals("""{"count":1}""", raw)
    }

    @Test
    fun customer_cannotWriteOnOtherUsersPurchases() = runBlocking {
        val own = fulanoBuys()
        val ownItem = items().fetch(PurchaseItemCriteria().withPurchaseId(own.id)).single()

        assertHttp(403) { items().insert(newItem(DBReset.FLASH_DRIVE_2GB_ID, DBReset.ADMIN_FIRST_PURCHASE_ID)) }
        assertHttp(403) { items().insert(newItem(DBReset.FLASH_DRIVE_2GB_ID)) }
        // a customer updates no item — a purchase is a receipt
        assertHttp(403) {
            items().update(PurchaseItem().apply { id = DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID; amount = 50 }, null, PurchaseItem().apply { amount = ProjectionValues.i32 })
        }
        // nor move one of their items to someone else's purchase
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
        // the customer does not delete purchases — not even their own
        assertHttp(403) { purchases().delete(PurchaseCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID)) }
        assertHttp(403) { items().delete(PurchaseItemCriteria().withPurchaseItemId(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)) }
        assertHttp(403) { purchases().delete(PurchaseCriteria().withPurchaseId(own.id)) }

        env.loginAs("admin")
        assertEquals(3, purchases().count(PurchaseCriteria()))
        assertEquals(4, items().count(PurchaseItemCriteria()))
        assertEquals(1, items().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)!!.amount)
        assertEquals(own.id, items().fetchById(ownItem.id!!)!!.purchaseId)
        // the administrator deletes, and the purchase takes its items along
        assertEquals(1, purchases().delete(PurchaseCriteria().withPurchaseId(own.id)))
        assertEquals(3, items().count(PurchaseItemCriteria()))
    }

    @Test
    fun customer_addsItemsToTheirOwnPurchases_butChangesNothingOnce_written() = runBlocking {
        val own = fulanoBuys()

        val extra = newItem(DBReset.COFFEE_MAKER_ID, own.id)
        assertTrue(items().insert(extra))
        assertEquals(2, items().count(PurchaseItemCriteria()))
        // a purchase is a receipt: not even its buyer changes it
        assertHttp(403) { items().update(PurchaseItem().apply { id = extra.id; amount = 3 }, null, PurchaseItem().apply { amount = ProjectionValues.i32 }) }
        assertHttp(403) { purchases().update(Purchase().apply { id = own.id; buyDate = Clock.System.now() }, null, Purchase().apply { buyDate = ProjectionValues.instant }) }
        assertEquals(1, items().fetchById(extra.id!!)!!.amount)

        // the administrator does
        env.loginAs("admin")
        assertTrue(items().update(PurchaseItem().apply { id = extra.id; amount = 3 }, null, PurchaseItem().apply { amount = ProjectionValues.i32 }))
        assertEquals(3, items().fetchById(extra.id!!)!!.amount)
        assertTrue(purchases().update(Purchase().apply { id = own.id; buyDate = Clock.System.now() }, null, Purchase().apply { buyDate = ProjectionValues.instant }))
    }

    @Test
    fun withoutProjection_aPurchaseIsItsReceipt_cutToWhatTheRoleReads() = runBlocking {
        val own = fulanoBuys()

        // a query without a projection (fetchById would send the client's default: the own fields)
        val receipt = purchases().fetch(PurchaseCriteria().withPurchaseId(own.id)).single()
        assertNotNull(receipt.buyDate)
        assertEquals("John Doe", receipt.user!!.name)
        assertNull(receipt.user!!.userName, "the buyer by name only")
        val line = receipt.items!!.single()
        assertEquals(1, line.amount)
        assertEquals(16.0, line.price)
        assertEquals("USB Flash Drive 2GB", line.product!!.name)
        assertEquals(16.0, line.product!!.price)
        assertNull(line.product!!.description)

        // an item alone: quantity, price and product — the same line as in the receipt
        val item = items().fetch(PurchaseItemCriteria()).single()
        assertEquals(1, item.amount)
        assertEquals("USB Flash Drive 2GB", item.product!!.name)
        assertNull(item.purchase, "not in the default shape")
    }

    @Test
    fun theItemsOfAReceipt_areRestrictedToTheRequester_too() = runBlocking {
        env.loginAs("admin")
        // the administrator's receipts: their items come with them
        val adminReceipt = purchases().fetch(PurchaseCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID)).single()
        assertEquals(2, adminReceipt.items!!.size)

        // a customer projecting the items of their purchases sees only theirs (and has only their purchases)
        val own = fulanoBuys()
        val pv = ProjectionValues
        val projection = Purchase().apply {
            id = pv.i64
            items = mutableListOf(PurchaseItem().apply { id = pv.i64; amount = pv.i32 })
        }
        val receipts = purchases().fetch(PurchaseCriteria().withProjection(projection))
        assertEquals(listOf(own.id), receipts.map { it.id })
        assertEquals(1, receipts.single().items!!.size)
    }

    @Test
    fun manager_readsTheirOwnPurchases_andWritesNone() = runBlocking {
        env.loginAs("admin")
        val manager = User().apply { userName = "manager"; password = env.authClient!!.encryptPassword("m4nager"); name = "Mary Major"; roles = "MANAGER" }
        assertTrue(env.userRepo.insert(manager))
        val bought = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = manager.id }
            items = mutableListOf(newItem(DBReset.WILSON_BALL_ID))
        }
        assertTrue(purchases().insert(bought))

        env.loginAs("manager", "m4nager")
        assertEquals(listOf(bought.id), purchases().fetch(PurchaseCriteria()).map { it.id })
        assertEquals(1, items().count(PurchaseItemCriteria()))
        assertHttp(403) {
            purchases().insert(Purchase().apply { buyDate = Clock.System.now(); user = User().apply { id = manager.id } })
        }
        assertHttp(403) { items().insert(newItem(DBReset.WILSON_BALL_ID, bought.id)) }
        assertHttp(403) { purchases().delete(PurchaseCriteria().withPurchaseId(bought.id)) }
    }

    @Test
    fun alternativeGroups_doNotTravel() {
        env.loginAs("admin")
        for (path in listOf("purchase/fetch", "purchase/count", "purchase-item/fetch", "user/fetch", "product/delete")) {
            val e = assertThrows(BusinessException::class.java) {
                env.transport.postJson("/api/repo/$path", """{"anyOf":[{"userId":${DBReset.FULANO_ID}}]}""")
            }
            assertTrue(e.message!!.startsWith("HTTP 400") && "anyOf" in e.message!!, e.message)
        }
        // nor leave the client
        assertThrows(BusinessException::class.java) {
            runBlocking { purchases().fetch(PurchaseCriteria().withAnyOf(PurchaseCriteria().withUserId(DBReset.FULANO_ID))) }
        }
    }

    @Test
    fun rawResponses_neverCarryAPasswordKey() {
        env.loginAs("admin")
        // asking for the buyer's password goes through the user policy, which reads it to no one: refused, naming it
        val withPassword = """"user":{"id":1,"userName":"~","password":"~"}"""
        val refused = listOf(
            "/api/repo/purchase/fetch" to """{"projection":{"id":1,$withPassword}}""",
            "/api/repo/purchase/fetch-page" to """{"page":0,"pageSize":10,"projection":{"id":1,$withPassword}}""",
            "/api/repo/purchase/fetch-by-id" to """{"id":${DBReset.ADMIN_FIRST_PURCHASE_ID},"projection":{"id":1,$withPassword}}""",
        )
        for ((path, body) in refused) {
            val e = assertThrows(RuntimeException::class.java) { env.transport.postJson(path, body) }
            assertTrue(e.message!!.startsWith("HTTP 403") && "[password]" in e.message!!, e.message)
        }

        val user = """"user":{"id":1,"userName":"~"}"""
        val bodies = listOf(
            env.transport.postJson("/api/repo/purchase/fetch", """{"projection":{"id":1,$user}}"""),
            env.transport.postJson("/api/repo/purchase/fetch-page", """{"page":0,"pageSize":10,"projection":{"id":1,$user}}"""),
            env.transport.postJson("/api/repo/purchase/fetch-by-id", """{"id":${DBReset.ADMIN_FIRST_PURCHASE_ID},"projection":{"id":1,$user}}"""),
            env.transport.postJson("/api/repo/purchase/fetch", "{}"),
            env.transport.postJson("/api/repo/purchase-item/fetch", """{"projection":{"id":1,"purchaseId":1}}"""),
        )
        for (body in bodies) {
            assertTrue("admin" in body || "purchaseId" in body || "John Smith" in body, body)
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
