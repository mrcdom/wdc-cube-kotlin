package br.com.wdc.shopping.test

import br.com.wdc.shopping.presentation.presenter.open.login.LoginKeys
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.PurchasesPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.util.BasePresentationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShoppingLoginTest : BasePresentationTest() {

    @Test
    fun testLoginFirstAccess() = runBlocking {
        LoginKeys(app).flip()

        val rootView = app.getRootView()!!

        val mainContent = LoginViewMock.cast(rootView.contentView)
        assertTrue(mainContent.scope.errorCode == 0, "The user should not have been validated")
    }

    @Test
    fun testLoginFailsWithUnrecognizedUserOrPassword() = runBlocking {
        LoginKeys(app).flip()

        val rootView = app.getRootView()!!

        var loginView = LoginViewMock.cast(rootView.contentView)
        loginView.scope.userName = "admin"
        loginView.scope.password = "unrecognized password"
        loginView.scope.onEnter(null, null)

        // Check if it keeps bean login view
        loginView = LoginViewMock.cast(rootView.contentView)
        assertTrue(loginView.scope.errorCode == 1, "The user should not have been validated")
    }

    @Test
    fun testLoginSystemAccess() = runBlocking {
        LoginKeys(app).flip()

        val rootView = app.getRootView()!!

        val loginView = LoginViewMock.cast(rootView.contentView)
        loginView.scope.userName = "admin"
        loginView.scope.password = "admin"
        loginView.scope.onEnter(null, null)

        val restrictedView = RestrictedViewMock.cast(rootView.contentView)

        assertTrue(!restrictedView.scope.nickName.isNullOrBlank(), "Invalid user name")
        assertTrue(restrictedView.scope.cartItemCount >= 0, "The number of items in the cart cannot be negative")
        assertTrue(restrictedView.scope.errorCode == 0, "The user should have been validated")

        val purchasesState = restrictedView.getPurchasesPanelScope()
        val productsState = restrictedView.getProductsPanelScope()

        assertNotNull(purchasesState.purchases, "Missing purchase list")
        assertNotNull(productsState.products, "Missing product list")

        // Simulate the view reporting capacity (no real view in tests)
        val purchasesMock = PurchasesPanelViewMock.cast(restrictedView.scope.purchasesPanel?.let(app::viewOf))
        purchasesMock.scope.onItemSizeCapacityChanged(3)

        assertEquals("John Smith", restrictedView.scope.nickName)
        assertEquals(0, restrictedView.scope.cartItemCount)

        // Pagination metadata
        assertEquals(0, purchasesState.page)
        assertEquals(3, purchasesState.pageSize)
        assertEquals(2, purchasesState.totalCount)

        assertEquals(2, purchasesState.purchases.size)

        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID, purchasesState.purchases[0].id)
        assertEquals(47.97, purchasesState.purchases[0].total, 0.001)
        assertEquals(2, purchasesState.purchases[0].items.size)
        assertEquals("Wilson Ball", purchasesState.purchases[0].items[0])
        assertEquals("Thread Seal Tape", purchasesState.purchases[0].items[1])

        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ID, purchasesState.purchases[1].id)
        assertEquals(200.0, purchasesState.purchases[1].total, 0.001)
        assertEquals(1, purchasesState.purchases[1].items.size)
        assertEquals("Italian Design Coffee Maker", purchasesState.purchases[1].items[0])

        assertEquals(4, productsState.products!!.size)

        // in name order; the description is outside the panel's projection, so it does not come (null, not "unknown")
        assertEquals(
            listOf(DBReset.COFFEE_MAKER_ID, DBReset.THREAD_SEAL_TAPE_ID, DBReset.FLASH_DRIVE_2GB_ID, DBReset.WILSON_BALL_ID),
            productsState.products!!.map { it.id },
        )
        assertTrue(productsState.products!!.all { it.description == null })
    }
}
