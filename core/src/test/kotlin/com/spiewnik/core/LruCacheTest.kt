package com.spiewnik.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LruCacheTest {

    @Test fun `evicts the least-recently-used entry over capacity`() {
        val cache = LruCache<Int, String>(maxSize = 2)
        cache.put(1, "a")
        cache.put(2, "b")
        cache.put(3, "c") // evicts key 1
        assertNull(cache.get(1))
        assertEquals("b", cache.get(2))
        assertEquals("c", cache.get(3))
        assertEquals(2, cache.size())
    }

    @Test fun `access keeps an entry fresh`() {
        val cache = LruCache<Int, String>(maxSize = 2)
        cache.put(1, "a")
        cache.put(2, "b")
        cache.get(1)        // 1 becomes most-recently-used
        cache.put(3, "c")   // evicts 2, not 1
        assertEquals("a", cache.get(1))
        assertNull(cache.get(2))
    }

    @Test fun `getOrPut computes once then caches`() {
        val cache = LruCache<Int, String>(maxSize = 4)
        var calls = 0
        val create: (Int) -> String = { k -> calls++; "v$k" }
        assertEquals("v7", cache.getOrPut(7, create))
        assertEquals("v7", cache.getOrPut(7, create))
        assertEquals(1, calls)
        assertTrue(cache.keys().contains(7))
    }
}
