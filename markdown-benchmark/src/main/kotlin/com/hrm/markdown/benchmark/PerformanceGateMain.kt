package com.hrm.markdown.benchmark

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Locale

fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: PerformanceGateMain <baseline.json> <results.json>" }
    val baseline = Json.parseToJsonElement(File(args[0]).readText()).jsonObject
    val results = Json.parseToJsonElement(File(args[1]).readText()).jsonArray
    val budgets = baseline.requiredObject("benchmarks")
    val failures = mutableListOf<String>()

    for ((benchmarkSuffix, rawBudget) in budgets) {
        val budget = rawBudget.jsonObject
        val result = results
            .map { it.jsonObject }
            .singleOrNull {
                it.requiredString("benchmark").endsWith(benchmarkSuffix) &&
                    it.requiredString("mode") == "avgt"
            } ?: error("Missing benchmark result for $benchmarkSuffix")
        val primary = result.requiredObject("primaryMetric")
        val percentiles = primary.requiredObject("scorePercentiles")
        val secondary = result.requiredObject("secondaryMetrics")

        val averageUs = primary.requiredDouble("score")
        val p95Us = percentiles.requiredDouble("95.0")
        val throughputOpsPerSecond = 1_000_000.0 / averageUs
        val allocatedBytesPerOp = secondary.requiredObject("gc.alloc.rate.norm").requiredDouble("score")
        val peakHeapDeltaBytes = secondary.requiredObject("heap.peak.delta").requiredDouble("score")

        fun requireAtMost(metric: String, actual: Double, limitKey: String) {
            val limit = budget.requiredDouble(limitKey)
            if (actual > limit) failures += "$benchmarkSuffix $metric ${actual.fmt()} > ${limit.fmt()}"
        }

        fun requireAtLeast(metric: String, actual: Double, limitKey: String) {
            val limit = budget.requiredDouble(limitKey)
            if (actual < limit) failures += "$benchmarkSuffix $metric ${actual.fmt()} < ${limit.fmt()}"
        }

        requireAtMost("p95Us", p95Us, "maxP95Us")
        requireAtLeast("throughputOpsPerSecond", throughputOpsPerSecond, "minThroughputOpsPerSecond")
        requireAtMost("allocatedBytesPerOp", allocatedBytesPerOp, "maxAllocatedBytesPerOp")
        requireAtMost("peakHeapDeltaBytes", peakHeapDeltaBytes, "maxPeakHeapDeltaBytes")

        println(
            "$benchmarkSuffix: avg=${averageUs.fmt()}us, p95=${p95Us.fmt()}us, " +
                "throughput=${throughputOpsPerSecond.fmt()} ops/s, " +
                "alloc=${allocatedBytesPerOp.fmt(0)} B/op, peakDelta=${peakHeapDeltaBytes.fmt(0)} B"
        )
    }

    check(failures.isEmpty()) {
        "Performance regression gate failed:\n - ${failures.joinToString("\n - ")}"
    }
}

private fun JsonObject.requiredObject(key: String): JsonObject =
    getValue(key).jsonObject

private fun JsonObject.requiredString(key: String): String =
    getValue(key).jsonPrimitive.content

private fun JsonObject.requiredDouble(key: String): Double =
    getValue(key).jsonPrimitive.double

private fun Double.fmt(decimals: Int = 2): String = String.format(Locale.ROOT, "%.${decimals}f", this)
