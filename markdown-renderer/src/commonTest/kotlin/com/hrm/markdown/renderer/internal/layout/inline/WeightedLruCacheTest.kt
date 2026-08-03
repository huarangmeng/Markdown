package com.hrm.markdown.renderer.internal.layout.inline

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightedLruCacheTest {
    @Test
    fun should_evict_leastRecentlyUsedEntry_when_entryLimitIsExceeded() {
        val cache = WeightedLruCache<Int, String>(
            maxEntries = 2,
            maxEstimatedBytes = Long.MAX_VALUE,
            estimateValueBytes = { it.length.toLong() },
        )

        cache.getOrPut(1) { "one" }
        cache.getOrPut(2) { "two" }
        cache.getOrPut(1) { error("entry 1 should be cached") }
        cache.getOrPut(3) { "three" }
        cache.getOrPut(2) { "two-again" }

        val metrics = cache.snapshot()
        assertEquals(2, metrics.entryCount)
        assertEquals(1, metrics.hits)
        assertEquals(4, metrics.misses)
        assertEquals(2, metrics.evictions)
    }

    @Test
    fun should_evict_until_estimatedByteLimitIsSatisfied() {
        val cache = WeightedLruCache<Int, String>(
            maxEntries = 10,
            maxEstimatedBytes = 201,
            estimateValueBytes = { it.length.toLong() },
        )

        cache.getOrPut(1) { "12345" }
        cache.getOrPut(2) { "12345" }

        val metrics = cache.snapshot()
        assertEquals(1, metrics.entryCount)
        assertEquals(101, metrics.estimatedBytes)
        assertEquals(1, metrics.evictions)
    }

    @Test
    fun should_reset_statistics_without_dropping_cachedEntries() {
        val cache = WeightedLruCache<Int, String>(
            maxEntries = 2,
            maxEstimatedBytes = 1024,
            estimateValueBytes = { it.length.toLong() },
        )
        cache.getOrPut(1) { "one" }

        cache.resetStatistics()
        cache.getOrPut(1) { error("entry should remain after statistics reset") }

        val metrics = cache.snapshot()
        assertEquals(1, metrics.entryCount)
        assertEquals(1, metrics.hits)
        assertEquals(0, metrics.misses)
        assertEquals(0, metrics.evictions)
    }
}
