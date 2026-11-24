package br.com.wdc.shopping.test

import br.com.wdc.shopping.presentation.presenter.open.login.LoginKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.RootPresenterMock
import br.com.wdc.shopping.test.mock.viewimpl.CartViewMock
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import br.com.wdc.framework.commons.storage.JvmSessionStorage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** A page reload starts the application again on the same session: the cart comes back with it. */
class CartReloadTest {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private val session = JvmSessionStorage()
    private val apps = mutableListOf<RootPresenterMock>()

    @AfterEach
    fun tearDown() = apps.forEach { it.release() }

    private fun newApp() = RootPresenterMock(session).also { apps += it }

    private suspend fun signIn(app: RootPresenterMock, user: String, password: String): RestrictedViewMock {
        LoginKeys(app).flip()
        val loginView = LoginViewMock.cast(app.getRootView()!!.scope.contentView)
        loginView.scope.userName = user
        loginView.scope.password = password
        loginView.presenter.scope.onEnter(null, null)
        return RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
    }

    private suspend fun addToCart(app: RootPresenterMock, productId: Long, quantity: Int) {
        RestrictedViewMock.cast(app.getRootView()!!.scope.contentView).getProductsPanelScope().onOpenProduct(productId)
        val home = RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
        ProductViewMock.cast(home.scope.contentView).presenter.scope.onAddToCart(quantity)
    }

    private suspend fun cartAfterReload(): CartViewMock {
        val app = newApp()
        CartKeys(app).flip()
        val home = RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
        return CartViewMock.cast(home.scope.contentView)
    }

    @Test
    fun theCart_survivesAReload_withItsQuantities_andTheHeaderCount() = runBlocking {
        val first = newApp()
        signIn(first, "admin", "admin")
        addToCart(first, DBReset.FLASH_DRIVE_2GB_ID, 2)
        addToCart(first, DBReset.WILSON_BALL_ID, 1)
        addToCart(first, DBReset.FLASH_DRIVE_2GB_ID, 1) // a product already in the cart also counts

        val cart = cartAfterReload()
        assertEquals(listOf(DBReset.FLASH_DRIVE_2GB_ID to 3, DBReset.WILSON_BALL_ID to 1), cart.scope.items.map { it.id to it.quantity })
        assertTrue(cart.scope.items.all { !it.name.isNullOrBlank() && it.price > 0.0 }, "names and prices read again")
        val home = RestrictedViewMock.cast(apps.last().getRootView()!!.scope.contentView)
        assertEquals(4, home.presenter.scope.cartItemCount)
    }

    @Test
    fun anEmptiedCart_isForgotten() = runBlocking {
        val first = newApp()
        signIn(first, "admin", "admin")
        addToCart(first, DBReset.WILSON_BALL_ID, 1)
        val home = RestrictedViewMock.cast(first.getRootView()!!.scope.contentView)
        home.presenter.scope.onOpenCart()
        val cartView = CartViewMock.cast(RestrictedViewMock.cast(first.getRootView()!!.scope.contentView).scope.contentView)
        cartView.presenter.scope.onRemoveProduct(DBReset.WILSON_BALL_ID)
        assertNull(session.getString(HomePresenter.CART_KEY))

        assertTrue(cartAfterReload().scope.items.isEmpty())
    }

    @Test
    fun signingOut_forgetsTheCart() = runBlocking {
        val first = newApp()
        signIn(first, "admin", "admin")
        addToCart(first, DBReset.WILSON_BALL_ID, 1)
        assertNotNull(session.getString(HomePresenter.CART_KEY))
        RestrictedViewMock.cast(first.getRootView()!!.scope.contentView).presenter.scope.onExit()
        assertNull(session.getString(HomePresenter.CART_KEY))
    }

    @Test
    fun anotherUsersCart_isIgnored() = runBlocking {
        // the session kept a cart that belongs to someone else
        session.set(HomePresenter.CART_KEY, CartManager.Snapshot(-1L, mapOf(DBReset.WILSON_BALL_ID to 2)).format())
        val app = newApp()
        signIn(app, "admin", "admin")
        HomeKeys(app).flip()
        val home = RestrictedViewMock.cast(app.getRootView()!!.scope.contentView)
        assertEquals(0, home.presenter.scope.cartItemCount)
    }

    @Test
    fun snapshot_roundTrips_andRefusesWhatIsNotOne() {
        val snapshot = CartManager.Snapshot(7L, linkedMapOf(3L to 2, 1L to 5))
        assertEquals("7|3:2,1:5", snapshot.format())
        assertEquals(snapshot, CartManager.Snapshot.parse(snapshot.format()))
        assertEquals(CartManager.Snapshot(7L, emptyMap()), CartManager.Snapshot.parse("7|"))
        for (bad in listOf(null, "", "x|1:2", "7", "7|1:x", "7|1")) {
            assertNull(CartManager.Snapshot.parse(bad), bad)
        }
    }
}
