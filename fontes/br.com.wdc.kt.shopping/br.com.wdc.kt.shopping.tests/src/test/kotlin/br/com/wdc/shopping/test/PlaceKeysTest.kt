package br.com.wdc.shopping.test

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptKeys
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductViewMock
import br.com.wdc.shopping.test.mock.viewimpl.PurchasesPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.util.BasePresentationTest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The keys classes: unique prefixes, getters that never trust the address, and what a bad address leads to. */
class PlaceKeysTest : BasePresentationTest() {

    private val presentationSources = File("../br.com.wdc.kt.shopping.presentation/src")

    @Test
    fun prefixes_areTwoLetters_uniqueAcrossTheApplication_andEveryNameStartsWithItsOwn() {
        val keysFiles = presentationSources.walkTopDown().filter { it.isFile && it.name.endsWith("Keys.kt") }.toList()
        assertTrue(keysFiles.size >= 4, "keys files not found under ${presentationSources.absolutePath}")

        val prefixes = keysFiles.associateWith { file ->
            val prefix = Regex("""const val PREFIX = "([^"]*)"""").find(file.readText())?.groupValues?.get(1)
            assertNotNull(prefix, "${file.name} declares no PREFIX")
            assertTrue(Regex("[a-z]{2}").matches(prefix!!), "${file.name}: the prefix must be two lowercase letters, was '$prefix'")
            prefix
        }
        val repeated = prefixes.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(repeated.isEmpty(), "prefixes used by more than one keys class: $repeated")

        // every name a keys class declares starts with its own prefix
        for (file in keysFiles) {
            val names = Regex("""const val [A-Z_]+ = "([^"]*)"""").findAll(file.readText()).map { it.groupValues[1] }
                .filter { it != prefixes[file] }
            for (name in names) assertTrue(name.startsWith("\$PREFIX-"), "${file.name}: '$name' does not start with \$PREFIX-")
        }
    }

    @Test
    fun everyPlace_hasItsOwnKeys_evenWithoutNamesOfItsOwn() {
        // every place has <Place>Keys
        val expected = Routes.Place.entries.map(::keysNameOf).toSet()
        val found = presentationSources.walkTopDown().filter { it.isFile && it.name.endsWith("Keys.kt") }.map { it.nameWithoutExtension }.toSet()
        assertEquals(emptySet<String>(), expected - found, "places without keys")
    }

    private fun keysNameOf(place: Routes.Place) = place.name.lowercase().replaceFirstChar { it.uppercase() } + "Keys"

    @Test
    fun getters_neverTrustTheAddress() {
        fun productIdOf(query: String) = ProductKeys(app, CubeIntent.parse("product?$query")).productId
        assertEquals(7L, productIdOf("pr-id=7"))
        assertNull(productIdOf("pr-id=abc"))
        assertNull(productIdOf("pr-id=-3"))
        assertNull(productIdOf("pr-id=0"))
        assertNull(productIdOf("productId=7"), "the old name is no longer read")

        val receipt = ReceiptKeys(app, CubeIntent.parse("receipt?rc-id=x"))
        assertNull(receipt.purchaseId)
        assertFalse(receipt.purchaseMade, "an attribute never comes from the address")

        fun pageOf(query: String) = HomeKeys(app, CubeIntent.parse("home?$query")).purchasesPage
        assertEquals(2, pageOf("hm-page=2"))
        assertEquals(0, pageOf("hm-page=abc"))
        assertEquals(0, pageOf("hm-page=-2"))

        // the default leaves the address
        val keys = ProductKeys(app, CubeIntent.parse("product?pr-id=7"))
        keys.productId = null
        assertEquals("product", keys.intent.toString())
    }

    private suspend fun signIn() {
        Routes.login(app)
        val loginView = LoginViewMock.cast(app.getRootView()!!.state.contentView)
        loginView.state.userName = "admin"
        loginView.state.password = "admin"
        loginView.presenter.onEnter()
    }

    private fun home(): RestrictedViewMock = RestrictedViewMock.cast(app.getRootView()!!.state.contentView)

    @Test
    fun anAddressWithoutAUsableId_redirectsToTheHome() = runBlocking {
        signIn()
        for (address in listOf("product?pr-id=abc", "product", "receipt?rc-id=-1", "receipt", "public")) {
            app.go(address)
            assertNull(home().state.contentView, "$address: the home shows no inner place")
            assertEquals("home", app.newIntent().toString(), address)
        }
    }

    @Test
    fun navigatingFromTheState_putsTheNewNamesInTheAddress() = runBlocking {
        signIn()
        ProductKeys(app).apply { productId = DBReset.FLASH_DRIVE_2GB_ID }.go()
        val productView = ProductViewMock.cast(home().state.contentView)
        assertEquals(DBReset.FLASH_DRIVE_2GB_ID, productView.state.product!!.id)
        assertEquals("product?pr-id=${DBReset.FLASH_DRIVE_2GB_ID}", app.newIntent().toString())

        app.go("receipt?rc-id=${DBReset.ADMIN_SECOND_PURCHASE_ID}")
        assertEquals("receipt?rc-id=${DBReset.ADMIN_SECOND_PURCHASE_ID}", app.newIntent().toString())
    }

    @Test
    fun thePurchasesPage_isInTheAddress_andComesBackFromIt() = runBlocking {
        signIn()
        // the address brings page 1 before the panel knows its capacity: the first capacity keeps it
        app.go("home?hm-page=1")
        val panel = PurchasesPanelViewMock.cast(home().state.purchasesPanelView)
        panel.presenter.onItemSizeCapacityChanged(1) // admin has two purchases: one per page
        assertEquals(1, panel.state.page)
        assertEquals(listOf(DBReset.ADMIN_FIRST_PURCHASE_ID), panel.state.purchases.map { it.id }, "the older purchase, on the second page")
        assertEquals("home?hm-page=1", app.newIntent().toString())

        // changing page is published; the first page is the default and leaves the address
        panel.presenter.onPageChange(0)
        assertEquals("home", app.newIntent().toString())

        // a later capacity change goes back to the first page, as before
        panel.presenter.onPageChange(1)
        panel.presenter.onItemSizeCapacityChanged(2)
        assertEquals(0, panel.state.page)

        // the page survives a trip to another place and back
        panel.presenter.onItemSizeCapacityChanged(1)
        panel.presenter.onPageChange(1)
        ProductKeys(app).apply { productId = DBReset.FLASH_DRIVE_2GB_ID }.go()
        assertTrue(app.newIntent().toString().contains("hm-page=1"), app.newIntent().toString())
        HomeKeys(app).go()
        assertEquals(1, PurchasesPanelViewMock.cast(home().state.purchasesPanelView).state.page)
    }
}
