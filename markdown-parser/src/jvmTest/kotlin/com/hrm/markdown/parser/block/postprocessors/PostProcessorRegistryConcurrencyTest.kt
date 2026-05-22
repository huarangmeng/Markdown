package com.hrm.markdown.parser.block.postprocessors

import com.hrm.markdown.parser.ast.Document
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostProcessorRegistryConcurrencyTest {

    @Test
    fun should_not_lose_processors_when_registering_concurrently() {
        val registry = PostProcessorRegistry()
        val executed = ConcurrentHashMap.newKeySet<Int>()
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(64)

        try {
            repeat(64) { index ->
                executor.submit {
                    start.await()
                    registry.register(
                        recordingProcessor(
                            id = index,
                            priority = index,
                            sink = executed,
                        ),
                    )
                    done.countDown()
                }
            }

            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS), "Concurrent registration should finish in time")

            registry.processAll(Document())

            assertEquals(64, executed.size, "Concurrent register() calls should not lose processors")
            assertEquals((0 until 64).toSet(), executed)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun should_rebuild_sorted_snapshot_when_registration_happens_during_sort() {
        val registry = PostProcessorRegistry()
        val executed = CopyOnWriteArrayList<String>()
        val sortStarted = CountDownLatch(1)
        val allowSortToFinish = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        val existing = object : PostProcessor {
            override val priority: Int
                get() {
                    sortStarted.countDown()
                    assertTrue(
                        allowSortToFinish.await(5, TimeUnit.SECONDS),
                        "Sorting should be released in time",
                    )
                    return 20
                }

            override fun process(document: Document) {
                executed += "existing"
            }
        }

        try {
            registry.register(existing)
            registry.register(namedProcessor(name = "tail", priority = 30, sink = executed))

            val future = executor.submit<Unit> {
                registry.processAll(Document())
            }

            assertTrue(sortStarted.await(5, TimeUnit.SECONDS), "Sorting should start before concurrent register")
            registry.register(namedProcessor(name = "new", priority = 10, sink = executed))
            allowSortToFinish.countDown()

            future.get(5, TimeUnit.SECONDS)

            assertEquals(
                listOf("new", "existing", "tail"),
                executed.toList(),
                "A concurrent register() must invalidate the stale sorted snapshot",
            )
        } finally {
            allowSortToFinish.countDown()
            executor.shutdownNow()
        }
    }

    private fun recordingProcessor(
        id: Int,
        priority: Int,
        sink: MutableSet<Int>,
    ): PostProcessor {
        return object : PostProcessor {
            override val priority: Int = priority

            override fun process(document: Document) {
                sink += id
            }
        }
    }

    private fun namedProcessor(
        name: String,
        priority: Int,
        sink: MutableList<String>,
    ): PostProcessor {
        return object : PostProcessor {
            override val priority: Int = priority

            override fun process(document: Document) {
                sink += name
            }
        }
    }
}
