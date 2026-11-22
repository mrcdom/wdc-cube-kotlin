package br.com.wdc.shopping.test.service

import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelService
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductService
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptService
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.ShoppingTestEnvironment
import kotlin.time.Clock
import org.junit.jupiter.api.Assertions.*

/**
 * Test algorithms shared between [ShoppingServiceTest] and
 * [ShoppingServiceRestTest]. Receives the repositories explicitly
 * through [ShoppingTestEnvironment].
 */
object ShoppingServiceTestAlgorithm {

    suspend fun testPurchaseItemFetch(env: ShoppingTestEnvironment) {
        val pv = ProjectionValues

        val usrPrj = User()
        usrPrj.id = pv.i64
        usrPrj.userName = pv.str

        val prdPrj = Product()
        prdPrj.id = pv.i64
        prdPrj.name = pv.str

        val pchPrj = Purchase()
        pchPrj.id = pv.i64
        pchPrj.user = usrPrj
        pchPrj.buyDate = pv.instant

        val itemPrj = PurchaseItem()
        itemPrj.id = pv.i64
        itemPrj.amount = pv.i32
        itemPrj.product = prdPrj
        itemPrj.price = pv.f64
        itemPrj.purchase = pchPrj

        val purchaseItemList = env.purchaseItemRepo.fetch(
            PurchaseItemCriteria()
                .withUserId(DBReset.ADMIN_ID)
                .withProjection(itemPrj)
        )
        assertEquals(3, purchaseItemList.size, "purchaseItemList.size()")
    }

    suspend fun testFullShoppingWorkflow(env: ShoppingTestEnvironment) {
        // Authenticates through the repository
        val users = env.userRepo.fetch(
            UserCriteria().withUserName("admin").withProjection(Subject.projection()),
            limit = 1,
        )
        assertFalse(users.isEmpty(), "Missing subject")
        val subject = Subject.create(users[0])
        assertNotNull(subject, "Missing subject")

        assertTrue(subject!!.id is Long, "Subject.id must be a Long type")
        val userId = subject.id!!

        assertEquals(DBReset.ADMIN_ID, userId, "UserId must be administrator")

        assertEquals("John Smith", subject.nickName, "User name did not match")

        val products = env.productRepo.fetch(ProductCriteria())
            .mapNotNull { ProductInfo.create(it) }

        assertNotNull(products)
        assertEquals(4, products.size)
        assertEquals(DBReset.COFFEE_MAKER_ID, products[0].id)
        assertEquals(DBReset.WILSON_BALL_ID, products[1].id)
        assertEquals(DBReset.THREAD_SEAL_TAPE_ID, products[2].id)
        assertEquals(DBReset.FLASH_DRIVE_2GB_ID, products[3].id)

        for (product in products) {
            assertTrue(!product.name.isNullOrBlank(), "Product name can not be empty")
            assertTrue(product.image!!.endsWith(".png"), "Product image name can not end differently than .png")
            assertTrue(product.price >= 0.0, "Product price must be grater than or equal to 0.0")
            assertTrue(!product.description.isNullOrBlank(), "Product description can not be empty")

            val sameProduct = ProductService(env.productRepo).loadProductById(product.id)
            assertEquals(product.id, sameProduct.id)
            assertEquals(product.name, sameProduct.name)
            assertEquals(product.image, sameProduct.image)
            assertEquals(product.price, sameProduct.price, 0.001)
            assertEquals(product.description, sameProduct.description)
        }

        val homeService = PurchasesPanelService(env.purchaseRepo)

        var purchases: List<PurchaseInfo> = homeService.loadPurchases(
            PurchaseCriteria().withOrderBy(PurchaseCriteria.OrderBy.OLDEST_FIRST)
        )

        assertNotNull(purchases)
        assertEquals(2, purchases.size)

        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ID, purchases[0].id)
        assertNotNull(purchases[0].items)
        assertEquals(1, purchases[0].items.size)

        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID, purchases[1].id)
        assertNotNull(purchases[1].items)
        assertEquals(2, purchases[1].items.size)

        val purchase = Purchase()
        purchase.user = User()
        purchase.user!!.id = userId
        purchase.buyDate = Clock.System.now()
        purchase.items = mutableListOf()
        purchase.items!!.add(PurchaseItem().also { item ->
            item.product = Product()
            item.product!!.id = DBReset.FLASH_DRIVE_2GB_ID
            item.price = 55.0
            item.amount = 1
        })
        purchase.items!!.add(PurchaseItem().also { item ->
            item.product = Product()
            item.product!!.id = DBReset.THREAD_SEAL_TAPE_ID
            item.price = 5.0
            item.amount = 2
        })

        env.purchaseRepo.insert(purchase)
        val purchaseId = purchase.id!!
        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID + 1, purchaseId)

        purchases = homeService.loadPurchasesOfUser(userId)

        assertNotNull(purchases)
        assertEquals(3, purchases.size)

        val lastPurchase = purchases[0]
        assertEquals(purchaseId, lastPurchase.id)
        assertEquals(2, lastPurchase.items.size)
        assertEquals(65.0, lastPurchase.total)

        val receipt = ReceiptService(env.purchaseRepo).loadReceipt(purchaseId, userId)
        assertNotNull(receipt)
        assertEquals(65.0, receipt!!.total)
        assertEquals(2, receipt.items.size)

        // the receipt belongs to the buyer: for another user, the purchase does not exist
        val otherUser = if (userId == DBReset.FULANO_ID) DBReset.BEOTRANO_ID else DBReset.FULANO_ID
        assertNull(ReceiptService(env.purchaseRepo).loadReceipt(purchaseId, otherUser))

        val item0 = purchase.items!![0]
        assertEquals(item0.price, receipt.items[0].value)
        assertEquals(item0.amount, receipt.items[0].quantity)
        assertEquals("USB Flash Drive 2GB", receipt.items[0].description)

        val item1 = purchase.items!![1]
        assertEquals(item1.price, receipt.items[1].value)
        assertEquals(item1.amount, receipt.items[1].quantity)
        assertEquals("Thread Seal Tape", receipt.items[1].description)
    }
}
