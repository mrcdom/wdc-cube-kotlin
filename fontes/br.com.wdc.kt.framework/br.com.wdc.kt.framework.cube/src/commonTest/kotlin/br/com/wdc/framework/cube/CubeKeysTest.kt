package br.com.wdc.framework.cube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CubeKeysTest {

    private class TestApp : CubeApplication() {
        val calls = mutableListOf<String>()
        var stopHere = false

        override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
            calls += "init=$initialization deepest=$deepest"
            return !stopHere
        }
    }

    private class TestPresenter(app: TestApp) : AbstractCubePresenter<TestApp>(app)

    private open class TopKeys protected constructor(app: TestApp, intent: CubeIntent?, place: CubeTreePlace) :
        CubeKeys<TestApp>(app, intent, place) {

        constructor(app: TestApp, intent: CubeIntent? = null) : this(app, intent, PLACE)

        companion object {
            val PLACE = CubeTreePlace.ROOT.child("top") { app: TestApp -> TestPresenter(app) }
        }

        var page: Int
            get() = intent.getParameterAsInteger("tp-page") ?: 0
            set(value) = intent.setParameter("tp-page", value.takeIf { it > 0 })
    }

    private class LeafKeys(app: TestApp, intent: CubeIntent? = null) : TopKeys(app, intent, PLACE) {
        companion object {
            val PLACE = TopKeys.PLACE.child("leaf") { app: TestApp -> TestPresenter(app) }
        }
    }

    @Test
    fun aChild_knowsItsPath_andHasALargerIdThanItsParent() {
        assertSame(TopKeys.PLACE, LeafKeys.PLACE.parent)
        assertSame(CubeTreePlace.ROOT, TopKeys.PLACE.parent)
        assertNull(CubeTreePlace.ROOT.parent)
        assertEquals(listOf(TopKeys.PLACE, LeafKeys.PLACE), LeafKeys.PLACE.path, "the root is not a step")
        assertEquals(emptyList(), CubeTreePlace.ROOT.path)
        assertTrue(TopKeys.PLACE in CubeTreePlace.ROOT)
        assertTrue(LeafKeys.PLACE.id > TopKeys.PLACE.id)
        assertTrue(LeafKeys.PLACE in TopKeys.PLACE)
        assertFalse(TopKeys.PLACE in LeafKeys.PLACE)
        assertEquals("leaf", LeafKeys.PLACE.placeName, "the name is what the address carries, as given")
    }

    @Test
    fun aNameThatWouldBreakTheAddress_isRefused() {
        for (name in listOf("", " ", "a?b", "a&b", "a#b", "a=b")) {
            assertFailsWith<IllegalArgumentException>(name) { TopKeys.PLACE.child(name) { app: TestApp -> TestPresenter(app) } }
        }
    }

    @Test
    fun flip_stepsThroughThePath_andKeepsWhatWasSet() = runTest {
        val app = TestApp()
        val keys = LeafKeys(app).apply { page = 3 }
        assertSame(LeafKeys.PLACE, keys.intent.place, "a new intent goes to the keys' place")

        assertTrue(keys.flip())
        assertNotNull(app.getPresenter(TopKeys.PLACE.id))
        assertNotNull(app.getPresenter(LeafKeys.PLACE.id))
        assertEquals(3, LeafKeys(app, keys.intent).page)

        assertTrue(TopKeys(app).flip())
        assertNull(app.getPresenter(LeafKeys.PLACE.id), "leaving the leaf releases its presenter")
        assertNotNull(app.getPresenter(TopKeys.PLACE.id))
    }

    @Test
    fun theApplication_isTheRoot_beforeEveryStep() = runTest {
        val app = TestApp()
        LeafKeys(app).flip()
        LeafKeys(app).flip()
        assertEquals(listOf("init=true deepest=false", "init=false deepest=false"), app.calls, "initialized once")

        // to the root itself: only the application, as the deepest
        assertTrue(app.flip(CubeTreePlace.ROOT))
        assertEquals("init=false deepest=true", app.calls.last())
        assertNull(app.getPresenter(TopKeys.PLACE.id), "the root alone keeps no presenter")

        // the application can stop the navigation at the root
        app.stopHere = true
        assertFalse(LeafKeys(app).flip())
        assertNull(app.getPresenter(TopKeys.PLACE.id))
    }
}
