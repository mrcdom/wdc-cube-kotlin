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

        val loginView = LoginViewMock.cast(rootView.scope.contentView)
        loginView.scope.userName = "admin"
        loginView.scope.password = "admin"
        loginView.presenter.scope.onEnter(null, null)

        return RestrictedViewMock.cast(rootView.scope.contentView)
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
    fun remoteCart_changesTheQuantity_byItsEventCode() = runBlocking {
        // the React cart sends code 4 with p.productId and p.quantity; the server ignored it before
        gotoRestricted().getProductsPanelScope().onOpenProduct(DBReset.WILSON_BALL_ID)
        var home = RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
        ProductViewMock.cast(home.scope.contentView).presenter.scope.onAddToCart(1)
        home = RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
        val cart = CartViewMock.cast(home.scope.contentView).presenter.scope

        cart.dispatch(4, mapOf("p.productId" to DBReset.WILSON_BALL_ID, "p.quantity" to 3))

        assertEquals(3, cart.items.single().quantity)
        assertEquals(3, home.presenter.scope.cartItemCount)
    }

    @Test
    fun openingAReceiptThatDoesNotExist_saysSo() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.getPurchasesPanelScope().onOpenReceipt(9_999_999L)
        val home = RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
        assertEquals(5, home.scope.errorCode, "receipt not found")
        assertEquals("Receipt code not found.", home.scope.errorMessage)
    }

    @Test
    fun buyingWithAnEmptyCart_saysTheCartIsEmpty() = runBlocking {
        gotoRestricted()
        CartKeys(app).flip()
        val home = RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
        val cartView = CartViewMock.cast(home.scope.contentView)
        cartView.presenter.scope.onBuy()
        assertEquals(3, cartView.scope.errorCode)
        assertEquals("There must be at least one item in the cart to complete a purchase.", cartView.scope.errorMessage)
    }

    @Test
    fun openingWithAnIdThatCannotExist_saysItIsRequired() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.getProductsPanelScope().onOpenProduct(Long.MIN_VALUE)
        assertEquals(6, RestrictedViewMock.cast(app.getRootView()!!.scope.contentView).scope.errorCode)
        restrictedView.getPurchasesPanelScope().onOpenReceipt(0)
        assertEquals(7, RestrictedViewMock.cast(app.getRootView()!!.scope.contentView).scope.errorCode)
    }

    @Test
    fun receiptItems_keepTheirIds_andComeOrderedByThem() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.getPurchasesPanelScope().onOpenReceipt(DBReset.ADMIN_SECOND_PURCHASE_ID)
        val home = RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
        val receipt = ReceiptViewMock.cast(home.scope.contentView).scope.receipt!!
        assertEquals(
            listOf(DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID, DBReset.ADMIN_SECOND_PURCHASE_ITEM1_ID),
            receipt.items.map { it.id },
        )
    }

    @Test
    fun purchasesPanel_pagePastTheEnd_showsTheLastPageWithItsItems() = runBlocking {
        val restrictedView = gotoRestricted()
        val panel = PurchasesPanelViewMock.cast(restrictedView.scope.purchasesPanelView)
        panel.scope.pageSize = 1
        panel.presenter.scope.onPageChange(50) // admin has two purchases: page 50 does not exist
        assertEquals(1, panel.scope.page, "adjusted to the last one")
        assertEquals(1, panel.scope.purchases.size, "with the items of the adjusted page")
    }

    @Test
    fun productsPanel_doesNotShowAMadeUpDescription_andComesInAStableOrder() = runBlocking {
        val restrictedView = gotoRestricted()
        val panel = ProductsPanelViewMock.cast(restrictedView.scope.productsPanelView)
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
        restrictedView = RestrictedViewMock.cast(rootView.scope.contentView)
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
        restrictedView = RestrictedViewMock.cast(rootView.scope.contentView)

        val productView = ProductViewMock.cast(restrictedView.scope.contentView)
        assertTrue(productView.scope.product != null, "A product must have been selected")
    }

    @Test
    fun testBuyProduct() = runBlocking {
        LoginKeys(app).flip()

        val rootView = app.getRootView()!!

        val loginView = LoginViewMock.cast(rootView.scope.contentView)
        loginView.scope.userName = "admin"
        loginView.scope.password = "admin"
        loginView.presenter.scope.onEnter(null, null)

        var homeView = RestrictedViewMock.cast(rootView.scope.contentView)

        homeView.getProductsPanelScope().onOpenProduct(DBReset.FLASH_DRIVE_2GB_ID)
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)
        var productView = ProductViewMock.cast(homeView.scope.contentView)
        assertTrue(productView.scope.product != null, "A product must have been selected")
        assertTrue(
            productView.scope.product!!.id == DBReset.FLASH_DRIVE_2GB_ID,
            "The product must be id==${DBReset.FLASH_DRIVE_2GB_ID}"
        )

        val homeUpdatesBefore = homeView.updates
        productView.presenter.scope.onAddToCart(1)
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)
        // the header counter is a field of the home state, kept by the cart's change listener
        assertEquals(1, homeView.presenter.scope.cartItemCount)
        assertTrue(homeView.updates > homeUpdatesBefore, "the cart change repaints the home header")
        var cartView = CartViewMock.cast(homeView.scope.contentView)
        assertTrue(cartView.scope.errorCode == 0, "There must be no error indication")
        assertTrue(cartView.scope.items.size == 1, "One item in the cart")
        assertTrue(cartView.scope.items[0].quantity == 1, "The item must have quantity 1")
        assertTrue(
            cartView.scope.items[0].id == DBReset.FLASH_DRIVE_2GB_ID,
            "ID in the cart <> ${DBReset.FLASH_DRIVE_2GB_ID}"
        )

        // the presenter no longer calls update(): its observed scope asks for the repaint
        var updatesBefore = cartView.updates
        cartView.presenter.scope.onModifyQuantity(DBReset.FLASH_DRIVE_2GB_ID, 0)
        assertTrue(cartView.updates > updatesBefore, "an alert repaints the cart")
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)
        cartView = CartViewMock.cast(homeView.scope.contentView)
        assertTrue(cartView.scope.errorCode == 1, "Invalid quantity indication")
        assertEquals("Some products have a quantity of less than one item. Unable to purchase.", cartView.scope.errorMessage)
        cartView.scope.errorCode = 0

        updatesBefore = cartView.updates
        cartView.presenter.scope.onModifyQuantity(DBReset.FLASH_DRIVE_2GB_ID, 2)
        assertTrue(cartView.updates > updatesBefore, "a new quantity repaints the cart")
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)
        cartView = CartViewMock.cast(homeView.scope.contentView)
        assertTrue(cartView.scope.errorCode == 0, "It must work without errors")
        assertTrue(cartView.scope.items[0].quantity == 2, "The item must have quantity 2")

        cartView.presenter.scope.onModifyQuantity(Long.MIN_VALUE, 2)
        cartView = CartViewMock.cast(homeView.scope.contentView)
        assertTrue(cartView.scope.errorCode == 2, "Product not found")
        cartView.scope.errorCode = 0

        cartView.presenter.scope.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)

        homeView.getProductsPanelScope().onOpenProduct(DBReset.WILSON_BALL_ID)
        productView = ProductViewMock.cast(homeView.scope.contentView)
        assertTrue(
            productView.scope.product!!.id == DBReset.WILSON_BALL_ID,
            "Product WILSON_BALL not found"
        )

        productView.presenter.scope.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)

        homeView.getProductsPanelScope().onOpenProduct(DBReset.THREAD_SEAL_TAPE_ID)
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)
        productView = ProductViewMock.cast(homeView.scope.contentView)
        assertTrue(
            productView.scope.product!!.id == DBReset.THREAD_SEAL_TAPE_ID,
            "Product THREAD_SEAL_TAPE not found"
        )

        productView.presenter.scope.onAddToCart(1)
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)
        cartView = CartViewMock.cast(homeView.scope.contentView)
        assertTrue(cartView.scope.items.size == 2, "Two items in the cart")
        assertTrue(cartView.scope.items[1].quantity == 1, "The item must have quantity 1")

        cartView.presenter.scope.onBuy()
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)
        val receiptView = ReceiptViewMock.cast(homeView.scope.contentView)
        assertTrue(receiptView.scope.notifySuccess, "It must be flagged as a new receipt")
        assertNotNull(receiptView.scope.receipt)
        assertEquals(2, receiptView.scope.receipt!!.items.size)

        receiptView.presenter.scope.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.scope.contentView)
        assertNull(
            homeView.scope.contentView,
            "The restricted view should be showing the default content"
        )
    }
}
