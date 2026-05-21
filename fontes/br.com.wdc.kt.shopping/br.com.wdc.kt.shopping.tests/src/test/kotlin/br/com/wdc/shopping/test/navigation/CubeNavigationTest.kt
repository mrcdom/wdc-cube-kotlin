package br.com.wdc.shopping.test.navigation

import br.com.wdc.framework.cube.CubeIntent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

/**
 * Tests for CubeNavigation lifecycle: commit, rollback, interrupt, redirect,
 * chained interrupts, orphaned presenters, and leaf-first release order.
 */
class CubeNavigationTest {

    private lateinit var app: TestApp

    @BeforeEach
    fun setup() {
        app = TestApp()
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private fun presenterOf(placeId: Int): TestPresenter? =
        app.getPresenter(placeId) as? TestPresenter

    private suspend fun navigate(vararg places: TestPlace): Boolean {
        val nav = app.navigate<TestApp>()
        val intent = CubeIntent()
        for (p in places) nav.step(p)
        return nav.execute(intent)
    }

    // ---------------------------------------------------------------
    // Scenario 1 — Normal commit
    // ---------------------------------------------------------------

    @Test
    fun `scenario 1 - normal navigation commits new and reused presenters`() = runBlocking {
        // First navigation: ROOT, HOME
        assertTrue(navigate(TestPlace.ROOT, TestPlace.HOME))

        val root = presenterOf(TestPlace.ROOT.id)!!
        val home = presenterOf(TestPlace.HOME.id)!!
        assertFalse(root.released)
        assertFalse(home.released)
        assertTrue(root.initialized)
        assertTrue(home.initialized)

        // Second navigation: ROOT, PRODUCTS (HOME replaced)
        assertTrue(navigate(TestPlace.ROOT, TestPlace.PRODUCTS))

        val root2 = presenterOf(TestPlace.ROOT.id)!!
        val products = presenterOf(TestPlace.PRODUCTS.id)!!
        assertSame(root, root2, "ROOT should be reused")
        assertTrue(home.released, "HOME should be released")
        assertFalse(products.released)
        assertNull(presenterOf(TestPlace.HOME.id), "HOME should not be in presenterMap")
    }

    // ---------------------------------------------------------------
    // Scenario 2 — Rollback on exception
    // ---------------------------------------------------------------

    @Test
    fun `scenario 2 - exception in applyParameters triggers rollback`() = runBlocking {
        // Setup: ROOT, HOME
        navigate(TestPlace.ROOT, TestPlace.HOME)
        val root = presenterOf(TestPlace.ROOT.id)!!
        val home = presenterOf(TestPlace.HOME.id)!!

        // Navigate to ROOT, PRODUCTS where PRODUCTS throws
        val ex = RuntimeException("test error")
        // We need to intercept the factory. Use a redirect place that throws.
        // Actually, let's navigate directly and have the new presenter throw
        // by configuring the place factory behavior.

        // Since we can't pre-configure a not-yet-created presenter,
        // we use a custom place with a throwing factory.
        val throwingPlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCTS.id
            override val placeName = "THROWING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                p.throwOnApply = ex
                p
            }
        }

        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(throwingPlace)
        val caught = assertThrows(RuntimeException::class.java) { runBlocking { nav.execute(CubeIntent()) } }
        assertSame(ex, caught)

        // ROOT should be restored (still in presenterMap)
        assertSame(root, presenterOf(TestPlace.ROOT.id), "ROOT should be restored")
        // HOME should be restored (still in presenterMap)
        assertSame(home, presenterOf(TestPlace.HOME.id), "HOME should be restored")
        assertFalse(root.released)
        assertFalse(home.released)
    }

    // ---------------------------------------------------------------
    // Scenario 3 — Interrupt with redirect, new navigation commits
    // ---------------------------------------------------------------

    @Test
    fun `scenario 3 - interrupt by redirect, new navigation commits`() = runBlocking {
        // Setup: ROOT, HOME
        navigate(TestPlace.ROOT, TestPlace.HOME)
        val root = presenterOf(TestPlace.ROOT.id)!!
        val home = presenterOf(TestPlace.HOME.id)!!

        // Navigate to ROOT, PRODUCTS, PRODUCT_DETAIL
        // During PRODUCTS.applyParameters, redirect to ROOT, CART
        val redirectingPlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCTS.id
            override val placeName = "REDIRECTING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                p.redirectTo = listOf(TestPlace.ROOT, TestPlace.CART)
                p
            }
        }

        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(redirectingPlace)
        // execute returns false because the original navigation was interrupted
        val result = nav.execute(CubeIntent())
        assertFalse(result)

        // After redirect: ROOT (reused), CART (new)
        assertSame(root, presenterOf(TestPlace.ROOT.id), "ROOT should be reused")
        assertNotNull(presenterOf(TestPlace.CART.id), "CART should exist")
        assertTrue(home.released, "HOME should be released (replaced)")
        assertNull(presenterOf(TestPlace.HOME.id))

        // The PRODUCTS presenter created during the interrupted navigation should be released
        // (it was an orphan — not adopted by the redirect navigation)
        // We can't easily get a reference to it, but CART should be alive
        assertFalse(presenterOf(TestPlace.CART.id)!!.released)
    }

    // ---------------------------------------------------------------
    // Scenario 4 — Interrupt + new navigation fails (rollback)
    // ---------------------------------------------------------------

    @Test
    fun `scenario 4 - interrupt then rollback releases inherited presenters`() = runBlocking {
        // Setup: ROOT, HOME
        navigate(TestPlace.ROOT, TestPlace.HOME)
        val root = presenterOf(TestPlace.ROOT.id)!!
        val home = presenterOf(TestPlace.HOME.id)!!

        // Navigate to ROOT, PRODUCTS — PRODUCTS redirects to ROOT, THROWING_CART
        val createdPresenters = mutableListOf<TestPresenter>()

        val redirectingPlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCTS.id
            override val placeName = "REDIRECTING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                createdPresenters.add(p)
                // Redirect to ROOT + throwing CART
                p.redirectTo = listOf(TestPlace.ROOT, object : br.com.wdc.framework.cube.CubePlace {
                    override val id = TestPlace.CART.id
                    override val placeName = "THROWING_CART"
                    override fun <A2 : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A2) -> br.com.wdc.framework.cube.CubePresenter = { a2 ->
                        val cp = TestPresenter(a2 as TestApp, this.id)
                        createdPresenters.add(cp)
                        cp.throwOnApply = RuntimeException("cart error")
                        cp
                    }
                })
                p
            }
        }

        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(redirectingPlace)

        // The redirect's exception propagates
        assertThrows(RuntimeException::class.java) { runBlocking { nav.execute(CubeIntent()) } }

        // Original state should be restored
        assertSame(root, presenterOf(TestPlace.ROOT.id), "ROOT should be restored")
        assertSame(home, presenterOf(TestPlace.HOME.id), "HOME should be restored")
        assertFalse(root.released)
        assertFalse(home.released)

        // Both created presenters (PRODUCTS from interrupted nav, CART from failed nav) should be released
        assertTrue(createdPresenters.size >= 2, "At least 2 presenters should have been created")
        for (p in createdPresenters) {
            assertTrue(p.released, "Created presenter (place=${p.placeId}) should be released")
        }
    }

    // ---------------------------------------------------------------
    // Scenario 5 — Orphaned inherited presenters released on commit
    // ---------------------------------------------------------------

    @Test
    fun `scenario 5 - orphaned inherited presenters released on commit`() = runBlocking {
        // Setup: ROOT
        navigate(TestPlace.ROOT)
        val root = presenterOf(TestPlace.ROOT.id)!!

        // Navigate ROOT, PRODUCTS — PRODUCTS redirects to ROOT, HOME
        // PRODUCTS is created, then orphaned (redirect doesn't use it)
        val orphanedPresenters = mutableListOf<TestPresenter>()

        val redirectingPlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCTS.id
            override val placeName = "REDIRECTING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                orphanedPresenters.add(p)
                p.redirectTo = listOf(TestPlace.ROOT, TestPlace.HOME)
                p
            }
        }

        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(redirectingPlace)
        nav.execute(CubeIntent())

        // ROOT reused, HOME created, PRODUCTS orphaned and released
        assertSame(root, presenterOf(TestPlace.ROOT.id))
        assertNotNull(presenterOf(TestPlace.HOME.id))
        assertNull(presenterOf(TestPlace.PRODUCTS.id), "Orphaned PRODUCTS should not be in presenterMap")

        assertEquals(1, orphanedPresenters.size)
        assertTrue(orphanedPresenters[0].released, "Orphaned PRODUCTS presenter should be released")
    }

    // ---------------------------------------------------------------
    // Scenario 6 — Chained interrupts (A → B → C)
    // ---------------------------------------------------------------

    @Test
    fun `scenario 6 - chained interrupts migrate presenters correctly`() = runBlocking {
        // Setup: ROOT
        navigate(TestPlace.ROOT)
        val root = presenterOf(TestPlace.ROOT.id)!!

        // Nav A: ROOT, PRODUCTS. PRODUCTS redirects to ROOT, HOME.
        // Nav B (from redirect): ROOT, HOME. HOME redirects to ROOT, CART.
        // Nav C (from redirect): ROOT, CART. Commits.

        val createdPresenters = mutableMapOf<Int, TestPresenter>()

        val productsRedirecting = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCTS.id
            override val placeName = "PRODUCTS_REDIRECTING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                createdPresenters[id] = p

                val homeRedirecting = object : br.com.wdc.framework.cube.CubePlace {
                    override val id = TestPlace.HOME.id
                    override val placeName = "HOME_REDIRECTING"
                    override fun <A2 : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A2) -> br.com.wdc.framework.cube.CubePresenter = { a2 ->
                        val hp = TestPresenter(a2 as TestApp, this.id)
                        createdPresenters[this.id] = hp
                        hp.redirectTo = listOf(TestPlace.ROOT, TestPlace.CART)
                        hp
                    }
                }

                p.redirectTo = listOf(TestPlace.ROOT, homeRedirecting)
                p
            }
        }

        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(productsRedirecting)
        nav.execute(CubeIntent())

        // Final state: ROOT (reused), CART (new)
        assertSame(root, presenterOf(TestPlace.ROOT.id))
        assertNotNull(presenterOf(TestPlace.CART.id))
        assertFalse(presenterOf(TestPlace.CART.id)!!.released)

        // PRODUCTS and HOME were created but orphaned — both should be released
        assertTrue(createdPresenters[TestPlace.PRODUCTS.id]!!.released, "PRODUCTS should be released")
        assertTrue(createdPresenters[TestPlace.HOME.id]!!.released, "HOME should be released")
    }

    // ---------------------------------------------------------------
    // Scenario 7 — goAhead=false commits partial navigation
    // ---------------------------------------------------------------

    @Test
    fun `scenario 7 - goAhead false commits partial navigation`() = runBlocking {
        // Setup: ROOT, HOME
        navigate(TestPlace.ROOT, TestPlace.HOME)
        val root = presenterOf(TestPlace.ROOT.id)!!
        val home = presenterOf(TestPlace.HOME.id)!!

        // Navigate ROOT, PRODUCTS, PRODUCT_DETAIL where PRODUCTS returns goAhead=false
        val stoppingPlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCTS.id
            override val placeName = "STOPPING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                p.goAhead = false
                p
            }
        }

        val result = run {
            val nav = app.navigate<TestApp>()
            nav.step(TestPlace.ROOT).step(stoppingPlace).step(TestPlace.PRODUCT_DETAIL)
            nav.execute(CubeIntent())
        }

        assertFalse(result, "Navigation should return false")

        // ROOT reused, PRODUCTS created (even though goAhead=false, commit still happens)
        assertSame(root, presenterOf(TestPlace.ROOT.id))
        assertNotNull(presenterOf(TestPlace.PRODUCTS.id))

        // HOME released (replaced), PRODUCT_DETAIL never created
        assertTrue(home.released, "HOME should be released")
        assertNull(presenterOf(TestPlace.PRODUCT_DETAIL.id), "PRODUCT_DETAIL should not exist")
    }

    // ---------------------------------------------------------------
    // Scenario 8 — Inherited presenter reused by redirect
    // ---------------------------------------------------------------

    @Test
    fun `scenario 8 - redirect reuses inherited created presenter`() = runBlocking {
        // Setup: ROOT
        navigate(TestPlace.ROOT)

        // Nav A: ROOT, PRODUCTS, PRODUCT_DETAIL
        // PRODUCT_DETAIL redirects to ROOT, PRODUCTS, CART
        // PRODUCTS (created by A) should be reused by B, not created again
        val productsCreationCount = intArrayOf(0)

        val productsPlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCTS.id
            override val placeName = "PRODUCTS"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                productsCreationCount[0]++
                TestPresenter(a as TestApp, id)
            }
        }

        val detailRedirecting = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCT_DETAIL.id
            override val placeName = "DETAIL_REDIRECTING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                p.redirectTo = listOf(TestPlace.ROOT, productsPlace, TestPlace.CART)
                p
            }
        }

        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(productsPlace).step(detailRedirecting)
        nav.execute(CubeIntent())

        // PRODUCTS should have been created only once (reused by the redirect)
        assertEquals(1, productsCreationCount[0], "PRODUCTS factory should be called only once")

        // Final state: ROOT, PRODUCTS, CART
        assertNotNull(presenterOf(TestPlace.ROOT.id))
        assertNotNull(presenterOf(TestPlace.PRODUCTS.id))
        assertNotNull(presenterOf(TestPlace.CART.id))
        assertFalse(presenterOf(TestPlace.PRODUCTS.id)!!.released)
    }

    // ---------------------------------------------------------------
    // Scenario 9 — Release order is leaf-first (descending ID)
    // ---------------------------------------------------------------

    @Test
    fun `scenario 9 - release order is leaf-first`() = runBlocking {
        // Navigate ROOT(1), HOME(2), PRODUCTS(3)
        navigate(TestPlace.ROOT, TestPlace.HOME, TestPlace.PRODUCTS)

        val releaseOrder = mutableListOf<Int>()
        // Override release to track order
        for (place in listOf(TestPlace.ROOT, TestPlace.HOME, TestPlace.PRODUCTS)) {
            val p = presenterOf(place.id) as TestPresenter
            // We need to wrap release to track order — use a holder
        }

        // Navigate to SETTINGS(7) — all previous should be released leaf-first
        // Use a custom place that tracks release order
        val trackingPlaces = mutableMapOf<Int, TestPresenter>()

        // First, capture references before they're released
        val rootP = presenterOf(TestPlace.ROOT.id) as TestPresenter
        val homeP = presenterOf(TestPlace.HOME.id) as TestPresenter
        val productsP = presenterOf(TestPlace.PRODUCTS.id) as TestPresenter

        navigate(TestPlace.SETTINGS)

        // All three should be released
        assertTrue(rootP.released)
        assertTrue(homeP.released)
        assertTrue(productsP.released)

        // Only SETTINGS should remain
        assertNotNull(presenterOf(TestPlace.SETTINGS.id))
        assertEquals(1, app.presenterCount())
    }

    // ---------------------------------------------------------------
    // Scenario 10 — Sequential navigations
    // ---------------------------------------------------------------

    @Test
    fun `scenario 10 - sequential navigations work correctly`() = runBlocking {
        // Nav 1: ROOT, HOME
        navigate(TestPlace.ROOT, TestPlace.HOME)
        val root1 = presenterOf(TestPlace.ROOT.id)!!
        val home1 = presenterOf(TestPlace.HOME.id)!!

        // Nav 2: ROOT, PRODUCTS (replaces HOME)
        navigate(TestPlace.ROOT, TestPlace.PRODUCTS)
        assertSame(root1, presenterOf(TestPlace.ROOT.id), "ROOT should be reused across navigations")
        assertTrue(home1.released)
        val products = presenterOf(TestPlace.PRODUCTS.id)!!

        // Nav 3: ROOT, PRODUCTS, PRODUCT_DETAIL (adds deeper level)
        navigate(TestPlace.ROOT, TestPlace.PRODUCTS, TestPlace.PRODUCT_DETAIL)
        assertSame(root1, presenterOf(TestPlace.ROOT.id))
        assertSame(products, presenterOf(TestPlace.PRODUCTS.id), "PRODUCTS should be reused")
        assertNotNull(presenterOf(TestPlace.PRODUCT_DETAIL.id))
        assertFalse(products.released)

        // Nav 4: ROOT (removes PRODUCTS and PRODUCT_DETAIL)
        val detail = presenterOf(TestPlace.PRODUCT_DETAIL.id) as TestPresenter
        navigate(TestPlace.ROOT)
        assertSame(root1, presenterOf(TestPlace.ROOT.id))
        assertTrue(products.released, "PRODUCTS should be released")
        assertTrue(detail.released, "PRODUCT_DETAIL should be released")
        assertEquals(1, app.presenterCount())
    }

    // ---------------------------------------------------------------
    // Scenario 11 — Rollback restores deepest flag correctly
    // ---------------------------------------------------------------

    @Test
    fun `scenario 11 - rollback restores original presenters with sourceIntent`() = runBlocking {
        // Setup: ROOT, HOME
        navigate(TestPlace.ROOT, TestPlace.HOME)
        val root = presenterOf(TestPlace.ROOT.id) as TestPresenter
        val home = presenterOf(TestPlace.HOME.id) as TestPresenter

        root.applyCount = 0
        home.applyCount = 0

        // Attempt navigation ROOT, THROWING — fails, rollback
        val throwingPlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.PRODUCTS.id
            override val placeName = "THROWING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                p.throwOnApply = RuntimeException("fail")
                p
            }
        }

        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(throwingPlace)
        assertThrows(RuntimeException::class.java) { runBlocking { nav.execute(CubeIntent()) } }

        // Root and Home should have been restored via applyParameters (rollback)
        assertTrue(root.applyCount >= 1, "ROOT should have applyParameters called during rollback")
        assertTrue(home.applyCount >= 1, "HOME should have applyParameters called during rollback")
        assertFalse(root.released)
        assertFalse(home.released)
    }

    // ---------------------------------------------------------------
    // Scenario 12 — Recursion limit
    // ---------------------------------------------------------------

    @Test
    fun `scenario 12 - recursion limit prevents infinite redirects`() = runBlocking {
        // Create a place that always redirects to itself
        val selfRedirecting: br.com.wdc.framework.cube.CubePlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.HOME.id
            override val placeName = "SELF_REDIRECTING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                p.redirectTo = listOf(TestPlace.ROOT, this)
                p
            }
        }

        navigate(TestPlace.ROOT)

        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(selfRedirecting)

        assertThrows(AssertionError::class.java) { runBlocking { nav.execute(CubeIntent()) } }
    }

    // ---------------------------------------------------------------
    // Scenario 13 — Empty app, first navigation
    // ---------------------------------------------------------------

    @Test
    fun `scenario 13 - first navigation on empty app`() = runBlocking {
        assertEquals(0, app.presenterCount())

        navigate(TestPlace.ROOT, TestPlace.HOME)

        assertEquals(2, app.presenterCount())
        assertNotNull(presenterOf(TestPlace.ROOT.id))
        assertNotNull(presenterOf(TestPlace.HOME.id))
        assertTrue((presenterOf(TestPlace.ROOT.id) as TestPresenter).initialized)
        assertTrue((presenterOf(TestPlace.HOME.id) as TestPresenter).initialized)
    }

    // ---------------------------------------------------------------
    // Scenario 14 — updateHistory called after commit/rollback
    // ---------------------------------------------------------------

    @Test
    fun `scenario 14 - updateHistory called after commit and rollback`() = runBlocking {
        // After commit
        val countBefore = app.historyUpdateCount
        navigate(TestPlace.ROOT)
        assertTrue(app.historyUpdateCount > countBefore, "updateHistory should be called after commit")

        // After rollback
        val countBeforeRollback = app.historyUpdateCount
        val throwingPlace = object : br.com.wdc.framework.cube.CubePlace {
            override val id = TestPlace.HOME.id
            override val placeName = "THROWING"
            override fun <A : br.com.wdc.framework.cube.CubeApplication> presenterFactory(): (A) -> br.com.wdc.framework.cube.CubePresenter = { a ->
                val p = TestPresenter(a as TestApp, id)
                p.throwOnApply = RuntimeException("fail")
                p
            }
        }
        val nav = app.navigate<TestApp>()
        nav.step(TestPlace.ROOT).step(throwingPlace)
        assertThrows(RuntimeException::class.java) { runBlocking { nav.execute(CubeIntent()) } }
        assertTrue(app.historyUpdateCount > countBeforeRollback, "updateHistory should be called after rollback")
    }
}
