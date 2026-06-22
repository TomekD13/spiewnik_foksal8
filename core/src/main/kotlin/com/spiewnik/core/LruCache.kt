package com.spiewnik.core

/**
 * Small thread-safe LRU cache (access-ordered). Evicts the least-recently-used
 * entry once [maxSize] is exceeded. Shared eviction policy; the stored value type
 * is platform-specific (Android Bitmap, desktop ImageBitmap, …).
 */
class LruCache<K, V>(private val maxSize: Int) {

    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > maxSize
    }

    @Synchronized fun get(key: K): V? = map[key]

    @Synchronized fun put(key: K, value: V) { map[key] = value }

    @Synchronized fun getOrPut(key: K, create: (K) -> V): V =
        map[key] ?: create(key).also { map[key] = it }

    @Synchronized fun size(): Int = map.size

    @Synchronized fun keys(): List<K> = map.keys.toList()
}
