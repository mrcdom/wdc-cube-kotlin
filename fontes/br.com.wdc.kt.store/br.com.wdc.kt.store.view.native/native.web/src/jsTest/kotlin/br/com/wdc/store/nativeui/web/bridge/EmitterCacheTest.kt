package br.com.wdc.store.nativeui.web.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class EmitterCacheTest {

    private class Emitters(val id: Long)

    @Test
    fun sameKey_givesTheSameEmitters_acrossRenders() {
        var created = 0
        val cache = EmitterCache<Long, Emitters> { id -> created++; Emitters(id) }
        val first = cache[1L]
        assertSame(first, cache[1L], "the same object on every render")
        assertNotSame(first, cache[2L])
        assertEquals(2, created)
    }

    @Test
    fun retainOnly_dropsTheKeysNoLongerShown() {
        var created = 0
        val cache = EmitterCache<Long, Emitters> { id -> created++; Emitters(id) }
        val kept = cache[1L]
        cache[2L]
        cache.retainOnly(listOf(1L))
        assertSame(kept, cache[1L], "a key still shown keeps its emitters")
        cache[2L]
        assertEquals(3, created, "a key that left gets new emitters if it comes back")
    }
}
