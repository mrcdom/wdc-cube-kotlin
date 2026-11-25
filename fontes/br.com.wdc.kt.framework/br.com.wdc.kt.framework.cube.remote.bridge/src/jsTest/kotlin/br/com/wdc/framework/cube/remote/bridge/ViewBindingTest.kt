package br.com.wdc.framework.cube.remote.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A scope from the host, as a view reads it, and the views it refers to. */
class ViewBindingTest {

    private fun scopeFromHost(json: String): dynamic = JSON.parse(json)

    @Test
    fun aView_readsItsScope_byName() {
        val binding = ViewBinding("473dbdd7a36a:3")
        binding.setScope(scopeFromHost("""{"id":"473dbdd7a36a:3","nickName":"John Doe","cartItemCount":2,"total":12.5,"open":true,"productsPanelViewId":"a1b2c3d4e5f6:4","rowsViewIds":["r:1","r:2"],"product":{"name":"Ball"}}"""))

        val scope = binding.scope
        assertEquals("John Doe", scope.string("nickName"))
        assertEquals(2, scope.int("cartItemCount"))
        assertEquals(12.5, scope.double("total"))
        assertTrue(scope.boolean("open"))
        assertFalse(scope.boolean("missing"))
        assertNull(scope.string("missing"))
        assertEquals("a1b2c3d4e5f6:4", scope.viewId("productsPanel"))
        assertEquals(listOf("r:1", "r:2"), scope.viewIds("rows"))
        assertEquals("Ball", scope.obj("product")?.string("name"))
    }

    @Test
    fun aNewScope_replacesTheOld_andRepaints() {
        val binding = ViewBinding("v:1")
        var repaints = 0
        binding.onChange = { repaints++ }
        binding.setScope(scopeFromHost("""{"id":"v:1","errorMessage":"Wrong"}"""))
        binding.setScope(scopeFromHost("""{"id":"v:1"}"""))

        assertNull(binding.scope.string("errorMessage"), "a field left out is gone")
        assertEquals(2, repaints)
    }

    @Test
    fun theViewsAScopeRefersTo_areItsNestedViews() {
        val binding = ViewBinding("v:1")
        binding.setScope(scopeFromHost("""{"id":"v:1","contentViewId":"c:2","rowsViewIds":["r:3"],"emptyViewIds":[],"name":"x"}"""))
        assertEquals(setOf("c:2", "r:3"), binding.referencedViewIds)
    }
}
