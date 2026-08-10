package com.hrm.markdown.parser

import com.hrm.markdown.parser.block.postprocessors.PostProcessor
import com.hrm.markdown.parser.block.starters.BlockStarter
import com.hrm.markdown.parser.flavour.CommonMarkFlavour
import com.hrm.markdown.parser.flavour.ExtendedFlavour
import com.hrm.markdown.parser.flavour.FlavourCache
import com.hrm.markdown.parser.flavour.MarkdownFlavour
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FlavourCacheConcurrencyTest {
    @Test
    fun should_publishSingleCacheInstance_when_accessedConcurrently() {
        FlavourCache.clearAll()
        val workerCount = 16
        val callsPerWorker = 200
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workerCount)
        val results = ConcurrentLinkedQueue<FlavourCache>()

        try {
            val futures = List(workerCount) {
                executor.submit {
                    start.await()
                    repeat(callsPerWorker) {
                        results += FlavourCache.of(ExtendedFlavour)
                    }
                }
            }
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }

        val published = results.first()
        assertEquals(workerCount * callsPerWorker, results.size)
        results.forEach { assertSame(published, it) }
        assertEquals(1, FlavourCache.cacheSize)
    }

    @Test
    fun should_preserveAllRegistrations_when_registeredConcurrently() {
        FlavourCache.clearAll()
        val flavours = List(32) { CollidingFlavour() }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        try {
            val futures = flavours.map { flavour ->
                executor.submit {
                    start.await()
                    FlavourCache.registerSingleton(flavour)
                }
            }
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }

        for (flavour in flavours) {
            assertSame(FlavourCache.of(flavour), FlavourCache.of(flavour))
        }
        assertEquals(flavours.size, FlavourCache.cacheSize)
    }

    private class CollidingFlavour : MarkdownFlavour {
        override val blockStarters: List<BlockStarter> = CommonMarkFlavour.blockStarters
        override val postProcessors: List<PostProcessor> = emptyList()

        override fun hashCode(): Int = 1
    }
}
