package com.hrm.markdown.benchmark

import org.openjdk.jmh.infra.BenchmarkParams
import org.openjdk.jmh.infra.IterationParams
import org.openjdk.jmh.profile.InternalProfiler
import org.openjdk.jmh.results.AggregationPolicy
import org.openjdk.jmh.results.IterationResult
import org.openjdk.jmh.results.Result
import org.openjdk.jmh.results.ScalarResult
import java.lang.management.ManagementFactory

/** Reports the additional peak heap retained during each measured JMH iteration. */
class PeakHeapProfiler : InternalProfiler {
    private val heapPools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type.name == "HEAP" }
    private var baselineBytes: Long = 0

    override fun getDescription(): String = "Peak heap delta measured from JVM memory pools"

    override fun beforeIteration(benchmarkParams: BenchmarkParams, iterationParams: IterationParams) {
        baselineBytes = heapPools.sumOf { it.usage.used.coerceAtLeast(0) }
        heapPools.forEach { it.resetPeakUsage() }
    }

    override fun afterIteration(
        benchmarkParams: BenchmarkParams,
        iterationParams: IterationParams,
        result: IterationResult,
    ): Collection<Result<*>> {
        val peakBytes = heapPools.sumOf { it.peakUsage.used.coerceAtLeast(0) }
        val deltaBytes = (peakBytes - baselineBytes).coerceAtLeast(0)
        return listOf(
            ScalarResult(
                "heap.peak.delta",
                deltaBytes.toDouble(),
                "bytes",
                AggregationPolicy.MAX,
            )
        )
    }
}
