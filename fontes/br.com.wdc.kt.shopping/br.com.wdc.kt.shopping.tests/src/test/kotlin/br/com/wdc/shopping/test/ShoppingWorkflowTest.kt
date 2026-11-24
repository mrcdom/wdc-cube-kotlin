package br.com.wdc.shopping.test

import br.com.wdc.shopping.presentation.presenter.open.login.LoginKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.viewimpl.CartViewMock
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductsPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.PurchasesPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ReceiptViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.util.BasePresentationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShoppingWorkflowTest : BasePresentationTest() {

    private suspend fun gotoRestricted(): RestrictedViewMock {
        LoginKeys(app).flip()

        val rootView = app.getRootView()!!

        val loginView = LoginViewMock.cast(rootView.contentView)
        loginView.scope.userName = "admin"
        loginView.scope.password = "admin"
        loginView.scope.onEnter(null, null)

        return RestrictedViewMock.cast(rootView.contentView)
    }

    @Test
    fun unexpectedError_showsTheDetail_onlyWhenThereIsOne() = runBlocking {
        gotoRestricted()
        val log = br.com.wdc.framework.commons.log.Log.getLogger("ShoppingWorkflowTest")
        app.alertUnexpectedError(log, "Unexpected error", IllegalStateException("connection refused"))
        assertEquals("Unexpected error: connection refused", app.getRootView()!!.scope.errorMessage)
        app.alertUnexpectedError(log, "Unexpected error", IllegalStateException())
        assertEquals("Unexpected error", app.getRootView()!!.scope.errorMessage)
    }

    @Test
    fun remoteCartRow_changesTheQuantity_byItsEventCode() = runBlocking {
        // a row of the React cart sends code 2 with p.quantity (the server ignored the cart's quantity buttons before)
        gotoRestricted().getProductsPanelScope().onOpenProduct(DBReset.WILSON_BALL_ID)
        var home = RestrictedViewMock.cast(app.getRootView()!!.contentView)
        ProductViewMock.cast(home.contentView).scope.onAddToCart(1)
        home = RestrictedViewMock.cast(app.getRootView()!!.contentView)
        val cart = CartViewMock.cast(home.contentView).scope

        val row = cart.rows.single()
        row.dispatch(2, mapOf("p.quantity" to 3))

        assertEquals(3, row.quantity)
        assertSame(row, cart.rows.single(), "the row that stays is reused")
        assertEquals(3 * row.price, cart.total, 1e-9, "the total is computed on the server")
        assertEquals(3, home.scope.cartItemCount)
    }

    @Test
    fun openingAReceiptThatDoesNotExist_saysSo() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.getPurchasesPanelScope().onOpenReceipt(9_999_999L)
        val home = RestrictedViewMock.cast(app.getRootView()!!.contentView)
        assertEquals(5, home.scope.errorCode, "receipt not found")
        assertEquals("Receipt code not found.", home.scope.errorMessage)
    }

    @Test
    fun buyingWithAnEmptyCart_saysTheCartIsEmpty() = runBlocking {
        gotoRestricted()
        CartKeys(app).flip()
        val home = RestrictedViewMock.cast(app.getRootView()!!.contentView)
        val cartView = CartViewMock.cast(home.contentView)
        cartView.scope.onBuy()
        assertEquals(3, cartView.scope.errorCode)
        assertEquals("There must be at least one item in the cart to complete a purchase.", cartView.scope.errorMessage)
    }

    @Test
    fun openingWithAnIdThatCannotExist_saysItIsRequired() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.getProductsPanelScope().onOpenProduct(Long.MIN_VALUE)
        assertEquals(6, RestrictedViewMock.cast(app.getRootView()!!.contentView).scope.errorCode)
        restrictedView.getPurchasesPanelScope().onOpenReceipt(0)
        assertEquals(7, RestrictedViewMock.cast(app.getRootView()!!.contentView).scope.errorCode)
    }

    @Test
    fun receiptItems_keepTheirIds_andComeOrderedByThem() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.getPurchasesPanelScope().onOpenReceipt(DBReset.ADMIN_SECOND_PURCHASE_ID)
        val home = RestrictedViewMock.cast(app.getRootView()!!.contentView)
        val receipt = ReceiptViewMock.cast(home.contentView).scope.receipt!!
        assertEquals(
            listOf(DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID, DBReset.ADMIN_SECOND_PURCHASE_ITEM1_ID),
            receipt.items.map { it.id },
        )
    }

    @Test
    fun purchasesPanel_pagePastTheEnd_showsTheLastPageWithItsItems() = runBlocking {
        val restrictedView = gotoRestricted()
        val panel = PurchasesPanelViewMock.cast(restrictedView.scope.purchasesPanel?.let(app::viewOf))
        panel.scope.pageSize = 1
        panel.scope.onPageChange(50) // admin has two purchases: page 50 does not exist
        assertEquals(1, panel.scope.page, "adjusted to the last one")
        assertEquals(1, panel.scope.purchases.size, "with the items of the adjusted page")
    }

    @Test
    fun productsPanel_doesNotShowAMadeUpDescription_andComesInAStableOrder() = runBlocking {
        val restrictedView = gotoRestricted()
        val panel = ProductsPanelViewMock.cast(restrictedView.scope.productsPanel?.let(app::viewOf))
        val products = panel.scope.products!!
        assertTrue(products.none { it.description == "unknown" }, "the description was not read: it must not travel made up")
        assertEquals(products.map { it.name }.sortedBy { it!!.lowercase() }, products.map { it.name }, "ordered by name")
    }

    @Test
    fun testViewNonexistentProduct() = runBlocking {
        var restrictedView = gotoRestricted()
        val rootView = app.getRootView()!!

        // Product that does not exist
        restrictedView.getProductsPanelScope().onOpenProduct(9_999_999L)
        restrictedView = RestrictedViewMock.cast(rootView.contentView)
        assertTrue(
            restrictedView.scope.errorCode == 3,
            "The error code must indicate that the product does not exist: ${restrictedView.scope.errorCode}"
        )
    }

    @Test
    fun testViewProduct() = runBlocking {
        var restrictedView = gotoRestricted()
        val rootView = app.getRootView()!!

        restrictedView.getProductsPanelScope().onOpenProduct(DBReset.FLASH_DRIVE_2GB_ID)
        restrictedView = RestrictedViewMock.cast(rootView.contentView)

        val productView = ProductViewMock.cast(restrictedView.contentView)
        assertTrue(productView.scope.product != null, "A product must have been selected")
    }

    @Test
    fun testBuyProduct() = runBlocking {
        LoginKeys(app).flip()

        val rootView = app.getRootView()!!

        val loginView = LoginViewMock.cast(rootView.contentView)
        loginView.scope.userName = "admin"
        loginView.scope.password = "admin"
        loginView.scope.onEnter(null, null)

        var homeView = RestrictedViewMock.cast(rootView.contentView)

        homeView.getProductsPanelScope().onOpenProduct(DBReset.FLASH_DRIVE_2GB_ID)
        homeView = RestrictedViewMock.cast(rootView.contentView)
        var productView = ProductViewMock.cast(homeView.contentView)
        assertTrue(productView.scope.product != null, "A product must have been selected")
        assertTrue(
            productView.scope.product!!.id == DBReset.FLASH_DRIVE_2GB_ID,
            "The product must be id==${DBReset.FLASH_DRIVE_2GB_ID}"
        )

        val homeUpdatesBefore = homeView.updates
        productView.scope.onAddToCart(1)
        homeView = RestrictedViewMock.cast(rootView.contentView)
        // the header counter is a field of the home state, kept by the cart's change listener
        assertEquals(1, homeView.scope.cartItemCount)
        assertTrue(homeView.updates > homeUpdatesBefore, "the cart change repaints the home header")
        var cartView = CartViewMock.cast(homeView.contentView)
        assertTrue(cartView.scope.errorCode == 0, "There must be no error indication")
        assertTrue(cartView.scope.rows.size == 1, "One item in the cart")
        assertTrue(cartView.scope.rows[0].quantity == 1, "The item must have quantity 1")
        assertTrue(
            cartView.scope.rows[0].productId == DBReset.FLASH_DRIVE_2GB_ID,
            "ID in the cart <> ${DBReset.FLASH_DRIVE_2GB_ID}"
        )

        // the presenter no longer calls update(): its observed scope asks for the repaint
        var updatesBefore = cartView.updates
        cartView.scope.rows.single { it.productId == DBReset.FLASH_DRIVE_2GB_ID }.onModifyQuantity(0)
        assertTrue(cartView.updates > updatesBefore, "an alert repaints the cart")
        homeView = RestrictedViewMock.cast(rootView.contentView)
        cartView = CartViewMock.cast(homeView.contentView)
        assertTrue(cartView.scope.errorCode == 1, "Invalid quantity indication")
        assertEquals("Some products have a quantity of less than one item. Unable to purchase.", cartView.scope.errorMessage)
        cartView.scope.errorCode = 0

        updatesBefore = cartView.updates
        cartView.scope.rows.single { it.productId == DBReset.FLASH_DRIVE_2GB_ID }.onModifyQuantity(2)
        assertTrue(cartView.updates > updatesBefore, "a new quantity repaints the cart")
        homeView = RestrictedViewMock.cast(rootView.contentView)
        cartView = CartViewMock.cast(homeView.contentView)
        assertTrue(cartView.scope.errorCode == 0, "It must work without errors")
        assertTrue(cartView.scope.rows[0].quantity == 2, "The item must have quantity 2")

        cartView.scope.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.contentView)

        homeView.getProductsPanelScope().onOpenProduct(DBReset.WILSON_BALL_ID)
        productView = ProductViewMock.cast(homeView.contentView)
        assertTrue(
            productView.scope.product!!.id == DBReset.WILSON_BALL_ID,
            "Product WILSON_BALL not found"
        )

        productView.scope.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.contentView)

        homeView.getProductsPanelScope().onOpenProduct(DBReset.THREAD_SEAL_TAPE_ID)
        homeView = RestrictedViewMock.cast(rootView.contentView)
        productView = ProductViewMock.cast(homeView.contentView)
        assertTrue(
            productView.scope.product!!.id == DBReset.THREAD_SEAL_TAPE_ID,
            "Product THREAD_SEAL_TAPE not found"
        )

        productView.scope.onAddToCart(1)
        homeView = RestrictedViewMock.cast(rootView.contentView)
        cartView = CartViewMock.cast(homeView.contentView)
        assertTrue(cartView.scope.rows.size == 2, "Two items in the cart")
        assertTrue(cartView.scope.rows[1].quantity == 1, "The item must have quantity 1")

        cartView.scope.onBuy()
        homeView = RestrictedViewMock.cast(rootView.contentView)
        val receiptView = ReceiptViewMock.cast(homeView.contentView)
        assertTrue(receiptView.scope.notifySuccess, "It must be flagged as a new receipt")
        assertNotNull(receiptView.scope.receipt)
        assertEquals(2, receiptView.scope.receipt!!.items.size)

        receiptView.scope.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.contentView)
        assertNull(
            homeView.contentView,
            "The restricted view should be showing the default content"
        )
    }
}
