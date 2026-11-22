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

        val loginView = LoginViewMock.cast(rootView.state.contentView)
        loginView.state.userName = "admin"
        loginView.state.password = "admin"
        loginView.presenter.onEnter()

        return RestrictedViewMock.cast(rootView.state.contentView)
    }

    @Test
    fun unexpectedError_showsTheDetail_onlyWhenThereIsOne() = runBlocking {
        gotoRestricted()
        val log = br.com.wdc.framework.commons.log.Log.getLogger("ShoppingWorkflowTest")
        app.alertUnexpectedError(log, "Unexpected error", IllegalStateException("connection refused"))
        assertEquals("Unexpected error: connection refused", app.getRootView()!!.state.errorMessage)
        app.alertUnexpectedError(log, "Unexpected error", IllegalStateException())
        assertEquals("Unexpected error", app.getRootView()!!.state.errorMessage)
    }

    @Test
    fun openingAReceiptThatDoesNotExist_saysSo() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.presenter.onOpenReceipt(9_999_999L)
        val home = RestrictedViewMock.cast(app.getRootView()!!.state.contentView)
        assertEquals(5, home.state.errorCode, "receipt not found")
        assertEquals("Receipt code not found.", home.state.errorMessage)
    }

    @Test
    fun buyingWithAnEmptyCart_saysTheCartIsEmpty() = runBlocking {
        gotoRestricted()
        CartKeys(app).flip()
        val home = RestrictedViewMock.cast(app.getRootView()!!.state.contentView)
        val cartView = CartViewMock.cast(home.state.contentView)
        cartView.presenter.onBuy()
        assertEquals(3, cartView.state.errorCode)
        assertEquals("There must be at least one item in the cart to complete a purchase.", cartView.state.errorMessage)
    }

    @Test
    fun openingWithAnIdThatCannotExist_saysItIsRequired() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.presenter.onOpenProduct(Long.MIN_VALUE)
        assertEquals(6, RestrictedViewMock.cast(app.getRootView()!!.state.contentView).state.errorCode)
        restrictedView.presenter.onOpenReceipt(0)
        assertEquals(7, RestrictedViewMock.cast(app.getRootView()!!.state.contentView).state.errorCode)
    }

    @Test
    fun receiptItems_keepTheirIds_andComeOrderedByThem() = runBlocking {
        val restrictedView = gotoRestricted()
        restrictedView.presenter.onOpenReceipt(DBReset.ADMIN_SECOND_PURCHASE_ID)
        val home = RestrictedViewMock.cast(app.getRootView()!!.state.contentView)
        val receipt = ReceiptViewMock.cast(home.state.contentView).state.receipt!!
        assertEquals(
            listOf(DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID, DBReset.ADMIN_SECOND_PURCHASE_ITEM1_ID),
            receipt.items.map { it.id },
        )
    }

    @Test
    fun purchasesPanel_pagePastTheEnd_showsTheLastPageWithItsItems() = runBlocking {
        val restrictedView = gotoRestricted()
        val panel = PurchasesPanelViewMock.cast(restrictedView.state.purchasesPanelView)
        panel.state.pageSize = 1
        panel.presenter.onPageChange(50) // admin has two purchases: page 50 does not exist
        assertEquals(1, panel.state.page, "adjusted to the last one")
        assertEquals(1, panel.state.purchases.size, "with the items of the adjusted page")
    }

    @Test
    fun productsPanel_doesNotShowAMadeUpDescription_andComesInAStableOrder() = runBlocking {
        val restrictedView = gotoRestricted()
        val panel = ProductsPanelViewMock.cast(restrictedView.state.productsPanelView)
        val products = panel.state.products!!
        assertTrue(products.none { it.description == "unknown" }, "the description was not read: it must not travel made up")
        assertEquals(products.map { it.name }.sortedBy { it!!.lowercase() }, products.map { it.name }, "ordered by name")
    }

    @Test
    fun testViewNonexistentProduct() = runBlocking {
        var restrictedView = gotoRestricted()
        val rootView = app.getRootView()!!

        // Product that does not exist
        restrictedView.presenter.onOpenProduct(9_999_999L)
        restrictedView = RestrictedViewMock.cast(rootView.state.contentView)
        assertTrue(
            restrictedView.state.errorCode == 3,
            "The error code must indicate that the product does not exist: ${restrictedView.state.errorCode}"
        )
    }

    @Test
    fun testViewProduct() = runBlocking {
        var restrictedView = gotoRestricted()
        val rootView = app.getRootView()!!

        restrictedView.presenter.onOpenProduct(DBReset.FLASH_DRIVE_2GB_ID)
        restrictedView = RestrictedViewMock.cast(rootView.state.contentView)

        val productView = ProductViewMock.cast(restrictedView.state.contentView)
        assertTrue(productView.state.product != null, "A product must have been selected")
    }

    @Test
    fun testBuyProduct() = runBlocking {
        LoginKeys(app).flip()

        val rootView = app.getRootView()!!

        val loginView = LoginViewMock.cast(rootView.state.contentView)
        loginView.state.userName = "admin"
        loginView.state.password = "admin"
        loginView.presenter.onEnter()

        var homeView = RestrictedViewMock.cast(rootView.state.contentView)

        homeView.presenter.onOpenProduct(DBReset.FLASH_DRIVE_2GB_ID)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        var productView = ProductViewMock.cast(homeView.state.contentView)
        assertTrue(productView.state.product != null, "A product must have been selected")
        assertTrue(
            productView.state.product!!.id == DBReset.FLASH_DRIVE_2GB_ID,
            "The product must be id==${DBReset.FLASH_DRIVE_2GB_ID}"
        )

        val homeUpdatesBefore = homeView.updates
        productView.presenter.onAddToCart(1)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        // the header counter is a field of the home state, kept by the cart's change listener
        assertEquals(1, homeView.presenter.state.cartItemCount)
        assertTrue(homeView.updates > homeUpdatesBefore, "the cart change repaints the home header")
        var cartView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(cartView.state.errorCode == 0, "There must be no error indication")
        assertTrue(cartView.state.items.size == 1, "One item in the cart")
        assertTrue(cartView.state.items[0].quantity == 1, "The item must have quantity 1")
        assertTrue(
            cartView.state.items[0].id == DBReset.FLASH_DRIVE_2GB_ID,
            "ID in the cart <> ${DBReset.FLASH_DRIVE_2GB_ID}"
        )

        // the presenter no longer calls update(): its observed state asks for the repaint
        var updatesBefore = cartView.updates
        cartView.presenter.onModifyQuantity(DBReset.FLASH_DRIVE_2GB_ID, 0)
        assertTrue(cartView.updates > updatesBefore, "an alert repaints the cart")
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        cartView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(cartView.state.errorCode == 1, "Invalid quantity indication")
        assertEquals("Some products have a quantity of less than one item. Unable to purchase.", cartView.state.errorMessage)
        cartView.state.errorCode = 0

        updatesBefore = cartView.updates
        cartView.presenter.onModifyQuantity(DBReset.FLASH_DRIVE_2GB_ID, 2)
        assertTrue(cartView.updates > updatesBefore, "a new quantity repaints the cart")
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        cartView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(cartView.state.errorCode == 0, "It must work without errors")
        assertTrue(cartView.state.items[0].quantity == 2, "The item must have quantity 2")

        cartView.presenter.onModifyQuantity(Long.MIN_VALUE, 2)
        cartView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(cartView.state.errorCode == 2, "Product not found")
        cartView.state.errorCode = 0

        cartView.presenter.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.state.contentView)

        homeView.presenter.onOpenProduct(DBReset.WILSON_BALL_ID)
        productView = ProductViewMock.cast(homeView.state.contentView)
        assertTrue(
            productView.state.product!!.id == DBReset.WILSON_BALL_ID,
            "Product WILSON_BALL not found"
        )

        productView.presenter.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.state.contentView)

        homeView.presenter.onOpenProduct(DBReset.THREAD_SEAL_TAPE_ID)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        productView = ProductViewMock.cast(homeView.state.contentView)
        assertTrue(
            productView.state.product!!.id == DBReset.THREAD_SEAL_TAPE_ID,
            "Product THREAD_SEAL_TAPE not found"
        )

        productView.presenter.onAddToCart(1)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        cartView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(cartView.state.items.size == 2, "Two items in the cart")
        assertTrue(cartView.state.items[1].quantity == 1, "The item must have quantity 1")

        cartView.presenter.onBuy()
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        val receiptView = ReceiptViewMock.cast(homeView.state.contentView)
        assertTrue(receiptView.state.notifySuccess, "It must be flagged as a new receipt")
        assertNotNull(receiptView.state.receipt)
        assertEquals(2, receiptView.state.receipt!!.items.size)

        receiptView.presenter.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        assertNull(
            homeView.state.contentView,
            "The restricted view should be showing the default content"
        )
    }
}
