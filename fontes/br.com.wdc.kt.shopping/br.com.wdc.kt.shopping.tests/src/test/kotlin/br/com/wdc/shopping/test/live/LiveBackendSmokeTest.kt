package br.com.wdc.shopping.test.live

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.shopping.domain.security.PasswordUtil
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.persistence.client.HttpProductRepository
import br.com.wdc.shopping.persistence.client.HttpPurchaseItemRepository
import br.com.wdc.shopping.persistence.client.HttpPurchaseRepository
import br.com.wdc.shopping.persistence.client.HttpUserRepository
import br.com.wdc.shopping.persistence.client.OkHttpTransport
import br.com.wdc.shopping.persistence.client.RestAuthClient
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestTransactionService
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end smoke test against a **running backend**, along the same path as a REST client (Compose, native):
 * login, catalog, checkout in a remote transaction and statement.
 *
 * Only runs with `SHOPPING_LIVE_BACKEND_URL` in the environment (e.g. `http://localhost:8080`); without it, it is
 * skipped. The backend must have security on and the demo seed (user `fulano`). **It writes a purchase to that
 * backend's database.**
 */
class LiveBackendSmokeTest {

    private val baseUrl: String? = System.getenv("SHOPPING_LIVE_BACKEND_URL")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    @Test
    fun customer_logsIn_browses_buys_andSeesThePurchase() = runBlocking {
        assumeTrue(baseUrl != null, "set SHOPPING_LIVE_BACKEND_URL to run against a running backend")
        JsonInputFactory.installCommon()
        JsonOutputFactory.installCommon()
        CryptoProvider.BEAN.set(JceCryptoProvider())

        val transport = OkHttpTransport(baseUrl!!)
        val config = RestConfig(transport)
        val auth = RestAuthClient(config)
        config.setAuthClientInstance(auth)
        val users = HttpUserRepository(transport)
        val products = HttpProductRepository(transport)
        val purchases = HttpPurchaseRepository(transport)
        val items = HttpPurchaseItemRepository(transport)

        // login
        auth.login("fulano", PasswordUtil.hashPassword("fulano"))
        val me = users.fetch(UserCriteria()).single()
        assertEquals("fulano", me.userName)
        assertNull(me.password)

        // catalog
        val catalog = products.fetch(ProductCriteria().withOrderBy(ProductCriteria.OrderBy.CHEAPEST_FIRST))
        assertTrue(catalog.size >= 2, "catalog with ${catalog.size} product(s)")
        assertTrue(catalog.zipWithNext().all { (a, b) -> a.price!! <= b.price!! })
        assertNotNull(products.fetchImage(catalog.first().id!!))

        // a customer does not write to the catalog
        assertThrows(AccessDeniedException::class.java) {
            runBlocking { products.insert(Product().apply { name = "x"; price = 1.5; description = "x" }) }
        }

        // checkout, in the remote transaction the REST client registers
        val before = purchases.count(PurchaseCriteria())
        val serverSide = ShoppingTransactions.BEAN.getOrNull()
        ShoppingTransactions.BEAN.set(RestTransactionService(transport))
        val purchaseId = try {
            CartManager(purchases).apply {
                addProduct(ProductInfo().apply { id = catalog[0].id!!; price = catalog[0].price!!; name = catalog[0].name }, 2)
                addProduct(ProductInfo().apply { id = catalog[1].id!!; price = catalog[1].price!!; name = catalog[1].name }, 1)
            }.commit(Subject().apply { id = me.id })
        } finally {
            ShoppingTransactions.BEAN.set(serverSide)
        }
        assertNotNull(purchaseId)
        assertEquals(before + 1, purchases.count(PurchaseCriteria()))

        // statement: the new purchase comes first, with its items and products
        val pv = ProjectionValues
        val statement = purchases.fetchPage(
            PurchaseCriteria()
                .withOrderBy(PurchaseCriteria.OrderBy.MOST_RECENT_PURCHASE_FIRST)
                .withProjection(
                    Purchase().apply {
                        id = pv.i64
                        buyDate = pv.instant
                        this.items = pv.singletonList(
                            PurchaseItem().apply { id = pv.i64; amount = pv.i32; price = pv.f64; product = Product().apply { id = pv.i64; name = pv.str } },
                            PurchaseItemCriteria().withOrderBy(PurchaseItemCriteria.OrderBy.LARGEST_QUANTITY_FIRST),
                        )
                    }
                ),
            0, 5,
        )
        assertEquals(before + 1, statement.totalItems)
        val newest = statement.items.first()
        assertEquals(purchaseId, newest.id)
        assertEquals(listOf<Int?>(2, 1), newest.items!!.map { it.amount })
        assertEquals(listOf(catalog[0].name, catalog[1].name), newest.items!!.map { it.product!!.name })
        assertEquals(2, items.count(PurchaseItemCriteria().withPurchaseId(purchaseId)))

        auth.logout()
    }
}
