package br.com.wdc.shopping.test.transaction

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.domain.transaction.TransactionNotAllowedException
import br.com.wdc.framework.domain.transaction.TransactionRequiredException
import br.com.wdc.framework.persistence.transaction.RemoteTransactionOptions
import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.persistence.client.HttpProductRepository
import br.com.wdc.shopping.persistence.client.OkHttpTransport
import br.com.wdc.shopping.persistence.client.RestTransactionService
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * A transação remota dirigida pelo cliente: várias escritas HTTP participam da mesma transação física no
 * servidor (o mesmo `txId`) e confirmam ou se desfazem juntas.
 */
class RemoteTransactionRestTest {

    companion object {
        private val env = RestTestEnvironment(
            "wedocode-shopping-rest-remote-tx",
            remoteTransactionOptions = RemoteTransactionOptions(maxOpenPerOwner = 2),
        )

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun tx() = RestTransactionService(env.transport)

    private fun product(name: String) = Product().apply { this.name = name; price = 9.99; description = "$name desc" }

    private suspend fun exists(product: Product) = product.id != null && env.productRepo.fetchById(product.id!!) != null

    /** Outro cliente, com identidade própria, falando com o mesmo servidor. */
    private fun anotherClient() = OkHttpTransport("http://localhost:${env.port}")

    private fun begin(transport: OkHttpTransport): String =
        Regex("\"txId\":\"([^\"]+)\"").find(transport.postJson("/api/tx/begin", "{}"))!!.groupValues[1]

    private fun finish(transport: OkHttpTransport, txId: String, outcome: String): String {
        transport.transactionIdSupplier = { txId }
        try {
            return transport.postJson("/api/tx/$outcome", "{}")
        } finally {
            transport.transactionIdSupplier = null
        }
    }

    // :: Desfecho

    @Test
    fun commit_persistsAllWrites() = runBlocking {
        val p1 = product("rtx-commit-1")
        val p2 = product("rtx-commit-2")
        tx().required {
            env.productRepo.insert(p1)
            env.productRepo.insert(p2)
        }
        assertTrue(exists(p1))
        assertTrue(exists(p2))
    }

    @Test
    fun setRollbackOnly_discardsWrites() = runBlocking {
        val p = product("rtx-rollback-only")
        tx().required { t ->
            env.productRepo.insert(p)
            t.setRollbackOnly()
            assertTrue(t.isRollbackOnly())
        }
        assertFalse(exists(p))
    }

    @Test
    fun exception_rollsBackAllWritesAtomically_andKeepsItsType() = runBlocking {
        val p1 = product("rtx-atomic-1")
        val p2 = product("rtx-atomic-2")
        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                tx().required {
                    env.productRepo.insert(p1)
                    env.productRepo.insert(p2)
                    error("boom")
                }
            }
        }
        assertEquals("boom", e.message)
        assertFalse(exists(p1))
        assertFalse(exists(p2))
    }

    @Test
    fun writeRefusedByTheServer_rollsBackWhatCameBefore() = runBlocking {
        // a compra grava; o item, de produto inexistente, falha no servidor — nada fica
        assertThrows(RuntimeException::class.java) {
            runBlocking {
                tx().required {
                    val purchase = Purchase().apply { buyDate = Clock.System.now(); user = User().apply { id = DBReset.FULANO_ID } }
                    assertTrue(env.purchaseRepo.insert(purchase))
                    env.purchaseItemRepo.insert(
                        PurchaseItem().apply {
                            this.purchase = purchase
                            product = Product().apply { id = 987_654L }
                            amount = 1
                            price = 10.5
                        }
                    )
                }
            }
        }
        assertEquals(2, env.purchaseRepo.count(PurchaseCriteria()))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
    }

    @Test
    fun everyKindOfWrite_joinsTheTransaction_includingTheImageUpload() = runBlocking {
        val id = DBReset.CAFETEIRA_ID
        val original = env.productRepo.fetchImage(id)!!
        tx().required { t ->
            assertTrue(env.productRepo.update(Product().apply { this.id = id; name = "outro nome" }, null, Product().apply { name = "~" }))
            assertTrue(env.productRepo.updateImage(id, byteArrayOf(1, 2, 3)))
            assertEquals(1, env.purchaseItemRepo.delete(PurchaseItemCriteria().withPurchaseItemId(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)))
            t.setRollbackOnly()
        }
        assertEquals("Cafeteira", env.productRepo.fetchById(id)!!.name!!.substringBefore(' '))
        assertArrayEquals(original, env.productRepo.fetchImage(id))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
    }

    @Test
    fun readsInsideTheBlock_seeOnlyWhatIsCommitted() = runBlocking {
        val before = env.productRepo.count(ProductCriteria())
        tx().required {
            env.productRepo.insert(product("rtx-not-yet-visible"))
            // a leitura não participa da transação remota
            assertEquals(before, env.productRepo.count(ProductCriteria()))
        }
        assertEquals(before + 1, env.productRepo.count(ProductCriteria()))
    }

    // :: O checkout, como o cliente o executa

    /** Roda o bloco com o serviço de transação que um cliente REST registra (`RestRepositoryBootstrap`). */
    private suspend fun <T> asAClientApplication(block: suspend () -> T): T {
        val serverSide = ShoppingTransactions.BEAN.get()
        ShoppingTransactions.BEAN.set(tx())
        try {
            return block()
        } finally {
            ShoppingTransactions.BEAN.set(serverSide)
        }
    }

    private fun productInfo(id: Long) = ProductInfo().apply { this.id = id; price = 10.5; name = "p$id" }

    @Test
    fun checkoutOverRest_commitsThePurchaseAndItsItems() = runBlocking {
        val cart = CartManager(env.purchaseRepo).apply {
            addProduct(productInfo(DBReset.CAFETEIRA_ID), 1)
            addProduct(productInfo(DBReset.PEN_DRIVE2GB_ID), 2)
        }
        val purchaseId = asAClientApplication { cart.commit(Subject().apply { id = DBReset.FULANO_ID }) }

        assertEquals(2, env.purchaseItemRepo.count(PurchaseItemCriteria().withPurchaseId(purchaseId)))
    }

    @Test
    fun checkoutOverRest_thatFailsInTheMiddle_leavesNoOrphanPurchase() = runBlocking {
        val cart = CartManager(env.purchaseRepo).apply {
            addProduct(productInfo(DBReset.CAFETEIRA_ID), 1)
            addProduct(productInfo(987_654L), 1)
        }
        assertThrows(RuntimeException::class.java) {
            runBlocking { asAClientApplication { cart.commit(Subject().apply { id = DBReset.FULANO_ID }) } }
        }
        assertEquals(2, env.purchaseRepo.count(PurchaseCriteria()))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
        // a transação remota foi encerrada: o cliente continua podendo escrever
        assertTrue(env.productRepo.insert(product("rtx-after-failed-checkout")))
    }

    // :: Propagação

    @Test
    fun nestedRequired_participatesInTheSameTransaction() = runBlocking {
        val service = tx()
        val p1 = product("rtx-nested-1")
        val p2 = product("rtx-nested-2")
        service.required { outer ->
            env.productRepo.insert(p1)
            service.required { inner ->
                assertSame(outer, inner)
                env.productRepo.insert(p2)
            }
            service.mandatory { assertTrue(it.isActive()) }
            outer.setRollbackOnly()
        }
        assertFalse(exists(p1))
        assertFalse(exists(p2))
    }

    @Test
    fun requiresNew_runsInItsOwnTransaction() = runBlocking {
        val service = tx()
        val outer = product("rtx-outer")
        val independent = product("rtx-independent")
        service.required { t ->
            env.productRepo.insert(outer)
            service.requiresNew { env.productRepo.insert(independent) }
            // a externa continua valendo depois do bloco
            service.mandatory { assertSame(t, it) }
            t.setRollbackOnly()
        }
        assertFalse(exists(outer))
        assertTrue(exists(independent))
    }

    @Test
    fun notSupported_insideARemoteTransaction_readsButCannotWrite() = runBlocking {
        // Para o servidor, uma escrita sem o X-Tx-Id de quem tem transação aberta é um cabeçalho perdido: ele a
        // recusa. Suspender a transação remota serve, portanto, para ler — não para escrever por fora dela.
        val service = tx()
        val loose = product("rtx-loose")
        service.required { t ->
            service.notSupported { ctx ->
                assertFalse(ctx.isActive())
                assertEquals(4, env.productRepo.count(ProductCriteria()))
                assertThrows(TransactionConflictException::class.java) { runBlocking { env.productRepo.insert(loose) } }
            }
            service.mandatory { assertSame(t, it) }
        }
        assertFalse(exists(loose))
        // fora de qualquer transação remota, notSupported escreve normalmente
        service.notSupported { env.productRepo.insert(loose) }
        assertTrue(exists(loose))
    }

    @Test
    fun mandatory_never_andSupports_followTheContract() = runBlocking<Unit> {
        val service = tx()
        assertThrows(TransactionRequiredException::class.java) { runBlocking { service.mandatory { } } }
        assertFalse(service.supports { it.isActive() })
        assertFalse(service.never { it.isActive() })
        assertThrows(IllegalStateException::class.java) { runBlocking { service.supports { it.setRollbackOnly() } } }
        service.required {
            assertTrue(service.supports { it.isActive() })
            assertThrows(TransactionNotAllowedException::class.java) { runBlocking { service.never { } } }
        }
    }

    // :: Defesas do servidor

    @Test
    fun headerlessWrite_whileRemoteTxOpen_isRefusedWith409() = runBlocking {
        // O cliente abriu a transação mas, por falha de propagação, a escrita vai sem o X-Tx-Id. O servidor a
        // recusa em vez de confirmá-la sozinha, fora da transação.
        val client = anotherClient()
        val repo = HttpProductRepository(client)
        val txId = begin(client)
        try {
            val p = product("rtx-guard-orphan")
            val e = assertThrows(TransactionConflictException::class.java) { runBlocking { repo.insert(p) } }
            assertTrue(e.message!!.startsWith("HTTP 409"), e.message)
            assertEquals(0, env.productRepo.count(ProductCriteria().withName("rtx-guard-orphan")))
            // outro cliente não é afetado
            assertTrue(env.productRepo.insert(product("rtx-guard-other-client")))
        } finally {
            finish(client, txId, "rollback")
        }
        // encerrada a transação, o cliente volta a escrever
        assertTrue(repo.insert(product("rtx-guard-after")))
    }

    @Test
    fun transactionBelongsToWhoOpenedIt() = runBlocking<Unit> {
        val owner = anotherClient()
        val intruder = anotherClient()
        val txId = begin(owner)
        try {
            val e = assertThrows(AccessDeniedException::class.java) { finish(intruder, txId, "commit") }
            assertTrue(e.message!!.startsWith("HTTP 403"), e.message)

            intruder.transactionIdSupplier = { txId }
            assertThrows(AccessDeniedException::class.java) {
                runBlocking { HttpProductRepository(intruder).insert(product("rtx-intruder")) }
            }
        } finally {
            assertTrue("rolledback" in finish(owner, txId, "rollback"))
        }
        assertEquals(0, env.productRepo.count(ProductCriteria().withName("rtx-intruder")))
    }

    @Test
    fun outcomeIsIdempotent_andTheOppositeOneIsAConflict() {
        val client = anotherClient()
        val txId = begin(client)
        assertTrue("committed" in finish(client, txId, "commit"))
        assertTrue("committed" in finish(client, txId, "commit"))
        assertThrows(TransactionConflictException::class.java) { finish(client, txId, "rollback") }
    }

    @Test
    fun openTransactionsPerClient_areLimited_with429() {
        val client = anotherClient()
        val first = begin(client)
        val second = begin(client)
        try {
            val e = assertThrows(TransactionLimitExceededException::class.java) { begin(client) }
            assertTrue(e.message!!.startsWith("HTTP 429"), e.message)
        } finally {
            finish(client, first, "rollback")
            finish(client, second, "rollback")
        }
        // liberadas as vagas, abre de novo
        finish(client, begin(client), "rollback")
    }

    @Test
    fun outcomeWithoutTheHeader_isRefused() {
        assertThrows(AccessDeniedException::class.java) { anotherClient().postJson("/api/tx/commit", "{}") }
    }

    @Test
    fun status_tellsWhatHappenedToATransaction() {
        val client = anotherClient()
        fun status(txId: String): String {
            val request = HttpRequest.newBuilder(URI("http://localhost:${env.port}/api/tx/status"))
                .header("X-Tx-Id", txId).header("X-Client-Id", client.clientId).GET().build()
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
        }

        val committed = begin(client)
        assertEquals("""{"status":"open"}""", status(committed))
        finish(client, committed, "commit")
        assertEquals("""{"status":"committed"}""", status(committed))

        val rolledBack = begin(client)
        finish(client, rolledBack, "rollback")
        assertEquals("""{"status":"rolledback"}""", status(rolledBack))
        assertEquals("""{"status":"unknown"}""", status("no-such-transaction"))
    }

    // :: Erros tipados no cliente

    @Test
    fun refusedRequests_arriveAsTheExceptionTheServerThrew() = runBlocking<Unit> {
        val e = assertThrows(InvalidRequestException::class.java) { runBlocking { env.productRepo.delete(ProductCriteria()) } }
        assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
    }
}
