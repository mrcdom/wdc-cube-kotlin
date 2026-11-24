package br.com.wdc.store.test

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeKeys
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.presentation.presenter.open.login.LoginKeys
import br.com.wdc.store.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.store.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptKeys
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.mock.viewimpl.LoginViewMock
import br.com.wdc.store.test.mock.viewimpl.ProductViewMock
import br.com.wdc.store.test.mock.viewimpl.PurchasesPanelViewMock
import br.com.wdc.store.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.store.test.util.BasePresentationTest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The keys classes: unique prefixes, one keys class per place with the class tree as the place tree, getters that
 * never trust the address, and what a bad address leads to.
 */
class PlaceKeysTest : BasePresentationTest() {

    private val presentationSources = File("../br.com.wdc.kt.store.presentation/src")

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
    fun eachPlace_isDeclaredByOneKeysClass_childOfTheKeysOfItsParent() {
        // the keys classes, by the source files: CubeKeys is in another module, so a scan of the classpath would not do
        val keysClasses = presentationSources.walkTopDown().filter { it.isFile && it.name.endsWith("Keys.kt") }.map { file ->
            val pkg = Regex("""^package ([\w.]+)""", RegexOption.MULTILINE).find(file.readText())!!.groupValues[1]
            Class.forName("$pkg.${file.nameWithoutExtension}")
        }.toList()
        keysClasses.forEach { assertTrue(CubeKeys::class.java.isAssignableFrom(it), "${it.simpleName} does not extend CubeKeys") }

        val placeOf = keysClasses.associateWith(::placeOf)
        assertEquals(placeOf.size, placeOf.values.toSet().size, "a place declared by more than one keys class")
        assertEquals(RootPresenter.PLACES.toSet() + CubeTreePlace.ROOT, placeOf.values.toSet(), "the places of the keys are the places of the application, and the root")

        for ((keys, place) in placeOf) {
            if (keys == RootKeys::class.java) {
                assertSame(CubeTreePlace.ROOT, place, "the root keys declare the root: the application itself")
            } else {
                assertSame(placeOf(keys.superclass), place.parent, "${keys.simpleName}: its place must be a child of the place of the keys it extends")
            }
            assertEquals(place, keysOf(keys).place, "${keys.simpleName}: its instances go to the place it declares")
        }
    }

    /** The `PLACE` a keys class declares in its companion (Kotlin keeps it in a static field of the class). */
    private fun placeOf(keys: Class<*>): CubeTreePlace =
        keys.getDeclaredField("PLACE").apply { isAccessible = true }.get(null) as CubeTreePlace

    private fun keysOf(keys: Class<*>): CubeKeys<*> =
        keys.getConstructor(RootPresenter::class.java, CubeIntent::class.java).newInstance(app, CubeIntent()) as CubeKeys<*>

    @Test
    fun anAddress_resolvesToItsPlace_andAnUnknownOneToTheRoot() {
        for (place in RootPresenter.PLACES) assertSame(place, app.placeOf(place.placeName))
        assertSame(RootKeys.PLACE, app.placeOf("nowhere"))
        assertSame(RootKeys.PLACE, app.placeOf(null))
    }

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
        LoginKeys(app).flip()
        val loginView = LoginViewMock.cast(app.getRootView()!!.contentView)
        loginView.scope.userName = "admin"
        loginView.scope.password = "admin"
        loginView.scope.onEnter(null, null)
    }

    private fun home(): RestrictedViewMock = RestrictedViewMock.cast(app.getRootView()!!.contentView)

    @Test
    fun anAddressWithoutAUsableId_redirectsToTheHome() = runBlocking {
        signIn()
        for (address in listOf("product?pr-id=abc", "product", "receipt?rc-id=-1", "receipt", "public")) {
            app.flip(address)
            assertNull(home().contentView, "$address: the home shows no inner place")
            assertEquals("home", app.newIntent().toString(), address)
        }
    }

    @Test
    fun navigatingFromTheState_putsTheNewNamesInTheAddress() = runBlocking {
        signIn()
        ProductKeys(app).apply { productId = DBReset.FLASH_DRIVE_2GB_ID }.flip()
        val productView = ProductViewMock.cast(home().contentView)
        assertEquals(DBReset.FLASH_DRIVE_2GB_ID, productView.scope.product!!.id)
        assertEquals("product?pr-id=${DBReset.FLASH_DRIVE_2GB_ID}", app.newIntent().toString())

        app.flip("receipt?rc-id=${DBReset.ADMIN_SECOND_PURCHASE_ID}")
        assertEquals("receipt?rc-id=${DBReset.ADMIN_SECOND_PURCHASE_ID}", app.newIntent().toString())
    }

    @Test
    fun thePurchasesPage_isInTheAddress_andComesBackFromIt() = runBlocking {
        signIn()
        // the address brings page 1 before the panel knows its capacity: the first capacity keeps it
        app.flip("home?hm-page=1")
        val panel = PurchasesPanelViewMock.cast(home().scope.purchasesPanel?.let(app.views::viewOf))
        panel.scope.onItemSizeCapacityChanged(1) // admin has two purchases: one per page
        assertEquals(1, panel.scope.page)
        assertEquals(listOf(DBReset.ADMIN_FIRST_PURCHASE_ID), panel.scope.purchases.map { it.id }, "the older purchase, on the second page")
        assertEquals("home?hm-page=1", app.newIntent().toString())

        // changing page is published; the first page is the default and leaves the address
        panel.scope.onPageChange(0)
        assertEquals("home", app.newIntent().toString())

        // a later capacity change goes back to the first page, as before
        panel.scope.onPageChange(1)
        panel.scope.onItemSizeCapacityChanged(2)
        assertEquals(0, panel.scope.page)

        // the page survives a trip to another place and back
        panel.scope.onItemSizeCapacityChanged(1)
        panel.scope.onPageChange(1)
        ProductKeys(app).apply { productId = DBReset.FLASH_DRIVE_2GB_ID }.flip()
        assertTrue(app.newIntent().toString().contains("hm-page=1"), app.newIntent().toString())
        HomeKeys(app).flip()
        assertEquals(1, PurchasesPanelViewMock.cast(home().scope.purchasesPanel?.let(app.views::viewOf)).scope.page)
    }
}
