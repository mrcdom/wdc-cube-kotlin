package br.com.wdc.shopping.test.service

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.utils.ProjectionValues
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
 * Algoritmos de teste compartilhados entre [ShoppingServiceTest] e
 * [ShoppingServiceRestTest]. Recebe os repositórios explicitamente
 * via [ShoppingTestEnvironment].
 */
object ShoppingServiceTestAlgorithm {

    fun testPurchaseItemFetch(env: ShoppingTestEnvironment) {
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
        pchPrj.buyDate = pv.offsetDateTime

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

    fun testFullShoppingWorkflow(env: ShoppingTestEnvironment) {
        // Autentica via repositório
        val users = env.userRepo.fetch(
            UserCriteria()
                .withUserName("admin")
                .withPassword("admin")
                .withProjection(Subject.projection())
                .withLimit(1)
        )
        assertFalse(users.isEmpty(), "Missing subject")
        val subject = Subject.create(users[0])
        assertNotNull(subject, "Missing subject")

        assertTrue(subject!!.id is Long, "Subject.id must be a Long type")
        val userId = subject.id!!

        assertEquals(DBReset.ADMIN_ID, userId, "UserId must be administrator")

        assertEquals("João da Silva", subject.nickName, "User name did not match")

        val produtos = env.productRepo.fetch(ProductCriteria())
            .mapNotNull { ProductInfo.create(it) }

        assertNotNull(produtos)
        assertEquals(4, produtos.size)
        assertEquals(DBReset.CAFETEIRA_ID, produtos[0].id)
        assertEquals(DBReset.BOLA_WILSON_ID, produtos[1].id)
        assertEquals(DBReset.FITA_VEDA_ROSCA_ID, produtos[2].id)
        assertEquals(DBReset.PEN_DRIVE2GB_ID, produtos[3].id)

        for (produto in produtos) {
            assertTrue(!produto.name.isNullOrBlank(), "Product name can not be empty")
            assertTrue(produto.image!!.endsWith(".png"), "Product image name can not end differently than .png")
            assertTrue(produto.price >= 0.0, "Product price must be grater than or equal to 0.0")
            assertTrue(!produto.description.isNullOrBlank(), "Product description can not be empty")

            val mesmoProduto = ProductService(env.productRepo).loadProductById(produto.id)
            assertEquals(produto.id, mesmoProduto.id)
            assertEquals(produto.name, mesmoProduto.name)
            assertEquals(produto.image, mesmoProduto.image)
            assertEquals(produto.price, mesmoProduto.price, 0.001)
            assertEquals(produto.description, mesmoProduto.description)
        }

        val homeService = PurchasesPanelService(env.purchaseRepo)

        var compras: List<PurchaseInfo> = homeService.loadPurchases(
            PurchaseCriteria().withOrderBy(PurchaseCriteria.OrderBy.ACENDING)
        )

        assertNotNull(compras)
        assertEquals(2, compras.size)

        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ID, compras[0].id)
        assertNotNull(compras[0].items)
        assertEquals(1, compras[0].items.size)

        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID, compras[1].id)
        assertNotNull(compras[1].items)
        assertEquals(2, compras[1].items.size)

        val purchase = Purchase()
        purchase.user = User()
        purchase.user!!.id = userId
        purchase.buyDate = Clock.System.now()
        purchase.items = mutableListOf()
        purchase.items!!.add(PurchaseItem().also { item ->
            item.product = Product()
            item.product!!.id = DBReset.PEN_DRIVE2GB_ID
            item.price = 55.0
            item.amount = 1
        })
        purchase.items!!.add(PurchaseItem().also { item ->
            item.product = Product()
            item.product!!.id = DBReset.FITA_VEDA_ROSCA_ID
            item.price = 5.0
            item.amount = 2
        })

        env.purchaseRepo.insert(purchase)
        val idCompra = purchase.id!!
        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID + 1, idCompra)

        compras = homeService.loadPurchasesOfUser(userId)

        assertNotNull(compras)
        assertEquals(3, compras.size)

        val ultimaCompra = compras[0]
        assertEquals(idCompra, ultimaCompra.id)
        assertEquals(2, ultimaCompra.items.size)
        assertEquals(65.0, ultimaCompra.total)

        val recibo = ReceiptService(env.purchaseRepo).loadReceipt(idCompra)
        assertNotNull(recibo)
        assertEquals(65.0, recibo!!.total)
        assertEquals(2, recibo.items.size)

        val pedido0 = purchase.items!![0]
        assertEquals(pedido0.price, recibo.items[0].value)
        assertEquals(pedido0.amount, recibo.items[0].quantity)
        assertEquals("Pen Drive 2GB", recibo.items[0].description)

        val pedido1 = purchase.items!![1]
        assertEquals(pedido1.price, recibo.items[1].value)
        assertEquals(pedido1.amount, recibo.items[1].quantity)
        assertEquals("Fita veda rosca", recibo.items[1].description)
    }
}
