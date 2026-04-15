package com.hrm.markdown.benchmark

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.BlankLine
import com.hrm.markdown.parser.ast.BlockQuote
import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.ast.IndentedCodeBlock
import com.hrm.markdown.parser.ast.ListBlock
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.SetextHeading
import com.hrm.markdown.parser.ast.Table
import kotlin.math.roundToLong

private const val DEFAULT_WARMUP_ITERATIONS = 5
private const val DEFAULT_MEASURE_ITERATIONS = 20

fun main(args: Array<String>) {
    val cli = BenchmarkCli.parse(args)
    val markdown = buildStreamingSampleMarkdown(sectionCount = cli.sectionCount)
    val chunks = chunkMarkdown(markdown, cli.chunkMode)

    println("Streaming Render Benchmark")
    println("scenario=${cli.scenarioName}")
    println("chunkMode=${cli.chunkMode.name.lowercase()}")
    println("chars=${markdown.length}, chunks=${chunks.size}, warmups=${cli.warmupIterations}, measures=${cli.measureIterations}")
    println()

    repeat(cli.warmupIterations) { iteration ->
        runStreamingIteration(chunks, cli.enablePagination)
        println("warmup ${iteration + 1}/${cli.warmupIterations} done")
    }

    val results = buildList {
        repeat(cli.measureIterations) {
            add(runStreamingIteration(chunks, cli.enablePagination))
        }
    }

    printSummary(results)
}

private data class BenchmarkCli(
    val warmupIterations: Int,
    val measureIterations: Int,
    val sectionCount: Int,
    val chunkMode: ChunkMode,
    val enablePagination: Boolean,
    val scenarioName: String,
) {
    companion object {
        fun parse(args: Array<String>): BenchmarkCli {
            var warmups = DEFAULT_WARMUP_ITERATIONS
            var measures = DEFAULT_MEASURE_ITERATIONS
            var sections = 18
            var chunkMode = ChunkMode.TOKEN
            var enablePagination = false
            var scenario = "streaming-guide"

            var index = 0
            while (index < args.size) {
                when (args[index]) {
                    "--warmups" -> warmups = args.getOrNull(index + 1)?.toIntOrNull() ?: warmups
                    "--iterations" -> measures = args.getOrNull(index + 1)?.toIntOrNull() ?: measures
                    "--sections" -> sections = args.getOrNull(index + 1)?.toIntOrNull() ?: sections
                    "--chunk-mode" -> {
                        chunkMode = args.getOrNull(index + 1)
                            ?.uppercase()
                            ?.let { value -> ChunkMode.entries.firstOrNull { it.name == value } }
                            ?: chunkMode
                    }
                    "--pagination" -> enablePagination = true
                    "--scenario" -> scenario = args.getOrNull(index + 1) ?: scenario
                }
                index += if (args[index].startsWith("--") && index + 1 < args.size && !args[index + 1].startsWith("--")) 2 else 1
            }

            return BenchmarkCli(
                warmupIterations = warmups.coerceAtLeast(0),
                measureIterations = measures.coerceAtLeast(1),
                sectionCount = sections.coerceAtLeast(1),
                chunkMode = chunkMode,
                enablePagination = enablePagination,
                scenarioName = scenario,
            )
        }
    }
}

private enum class ChunkMode {
    TOKEN,
    LINE,
    PARAGRAPH,
}

private data class IterationResult(
    val appendTotalNs: Long,
    val appendMaxNs: Long,
    val finalizeNs: Long,
    val blockFilterTotalNs: Long,
    val chunkCount: Int,
    val blockUpdateCount: Int,
    val finalBlockCount: Int,
) {
    val totalNs: Long get() = appendTotalNs + finalizeNs + blockFilterTotalNs
}

private fun runStreamingIteration(
    chunks: List<String>,
    enablePagination: Boolean,
): IterationResult {
    val parser = MarkdownParser()
    var previousBlocks = emptyList<Node>()
    var previousRevisions = emptyList<Long>()
    var appendTotalNs = 0L
    var appendMaxNs = 0L
    var finalizeNs = 0L
    var blockFilterTotalNs = 0L
    var blockUpdateCount = 0

    parser.beginStream()
    for (chunk in chunks) {
        val appendStart = System.nanoTime()
        val document = parser.append(chunk)
        val appendCost = System.nanoTime() - appendStart
        appendTotalNs += appendCost
        appendMaxNs = maxOf(appendMaxNs, appendCost)

        val blockFilterStart = System.nanoTime()
        val filtered = document.children.filter { it !is BlankLine }
        val effectiveBlocks = if (enablePagination) {
            filtered.take(100.coerceAtMost(filtered.size))
        } else {
            filtered
        }
        val revisions = effectiveBlocks.map(::benchmarkRenderRevision)
        if (!structurallyEqual(previousBlocks, effectiveBlocks) ||
            !revisionsEqual(previousRevisions, revisions)
        ) {
            previousBlocks = effectiveBlocks.toList()
            previousRevisions = revisions
            blockUpdateCount++
        }
        blockFilterTotalNs += System.nanoTime() - blockFilterStart
    }

    val finalizeStart = System.nanoTime()
    val finalDocument = parser.endStream()
    finalizeNs = System.nanoTime() - finalizeStart
    val finalBlocks = finalDocument.children.count { it !is BlankLine }

    return IterationResult(
        appendTotalNs = appendTotalNs,
        appendMaxNs = appendMaxNs,
        finalizeNs = finalizeNs,
        blockFilterTotalNs = blockFilterTotalNs,
        chunkCount = chunks.size,
        blockUpdateCount = blockUpdateCount,
        finalBlockCount = finalBlocks,
    )
}

private fun printSummary(results: List<IterationResult>) {
    val appendTotals = results.map { it.appendTotalNs }
    val appendMaxes = results.map { it.appendMaxNs }
    val finalizeTotals = results.map { it.finalizeNs }
    val blockFilterTotals = results.map { it.blockFilterTotalNs }
    val totalTotals = results.map { it.totalNs }
    val chunkCount = results.firstOrNull()?.chunkCount ?: 0
    val blockUpdates = results.map { it.blockUpdateCount }
    val finalBlockCount = results.firstOrNull()?.finalBlockCount ?: 0

    println("Summary")
    println("finalBlocks=$finalBlockCount")
    println("appendTotal avg=${appendTotals.averageMs()} ms, p95=${appendTotals.p95Ms()} ms")
    println("appendPerChunk avg=${appendTotals.averagePerChunkMs(chunkCount)} ms")
    println("appendChunkMax avg=${appendMaxes.averageMs()} ms, p95=${appendMaxes.p95Ms()} ms")
    println("blockFilter avg=${blockFilterTotals.averageMs()} ms, p95=${blockFilterTotals.p95Ms()} ms")
    println("finalize avg=${finalizeTotals.averageMs()} ms, p95=${finalizeTotals.p95Ms()} ms")
    println("total avg=${totalTotals.averageMs()} ms, p95=${totalTotals.p95Ms()} ms")
    println("blockUpdates avg=${blockUpdates.average().roundToLong()} per iteration")
}

private fun List<Long>.averageMs(): String = "%.3f".format(average() / 1_000_000.0)

private fun List<Long>.p95Ms(): String {
    if (isEmpty()) return "0.000"
    val sorted = sorted()
    val index = ((sorted.lastIndex) * 0.95).roundToLong().toInt().coerceIn(0, sorted.lastIndex)
    return "%.3f".format(sorted[index] / 1_000_000.0)
}

private fun List<Long>.averagePerChunkMs(chunkCount: Int): String {
    if (chunkCount <= 0) return "0.000"
    return "%.6f".format((average() / chunkCount) / 1_000_000.0)
}

private fun structurallyEqual(a: List<Node>, b: List<Node>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] !== b[i]) return false
    }
    return true
}

private fun revisionsEqual(a: List<Long>, b: List<Long>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] != b[i]) return false
    }
    return true
}

private fun benchmarkRenderRevision(node: Node): Long = when (node) {
    is Paragraph -> revisionHash(
        node.lineRange.endLine.toLong(),
        node.contentHash,
        (node.rawContent?.length ?: 0).toLong(),
    )
    is Heading -> revisionHash(
        node.level.toLong(),
        node.lineRange.endLine.toLong(),
        node.contentHash,
        (node.rawContent?.length ?: 0).toLong(),
    )
    is SetextHeading -> revisionHash(
        node.level.toLong(),
        node.lineRange.endLine.toLong(),
        node.contentHash,
        (node.rawContent?.length ?: 0).toLong(),
    )
    is FencedCodeBlock -> revisionHash(
        node.lineRange.endLine.toLong(),
        node.contentHash,
        node.literal.length.toLong(),
    )
    is IndentedCodeBlock -> revisionHash(
        node.lineRange.endLine.toLong(),
        node.contentHash,
        node.literal.length.toLong(),
    )
    is BlockQuote -> revisionHash(
        node.lineRange.endLine.toLong(),
        node.contentHash,
        node.childCount().toLong(),
    )
    is ListBlock -> revisionHash(
        node.lineRange.endLine.toLong(),
        node.contentHash,
        node.childCount().toLong(),
    )
    is Table -> revisionHash(
        node.lineRange.endLine.toLong(),
        node.contentHash,
        node.childCount().toLong(),
    )
    else -> revisionHash(node.lineRange.endLine.toLong(), node.contentHash)
}

private fun revisionHash(a: Long, b: Long): Long =
    mixRevision(mixRevision(REVISION_OFFSET_BASIS, a), b)

private fun revisionHash(a: Long, b: Long, c: Long): Long =
    mixRevision(revisionHash(a, b), c)

private fun revisionHash(a: Long, b: Long, c: Long, d: Long): Long =
    mixRevision(revisionHash(a, b, c), d)

private fun mixRevision(acc: Long, value: Long): Long = (acc xor value) * REVISION_FNV_PRIME

private const val REVISION_OFFSET_BASIS = -3750763034362895579L
private const val REVISION_FNV_PRIME = 1099511628211L

private fun chunkMarkdown(markdown: String, mode: ChunkMode): List<String> {
    return when (mode) {
        ChunkMode.TOKEN -> chunkByToken(markdown)
        ChunkMode.LINE -> markdown.splitToSequence('\n').mapIndexed { index, line ->
            if (index == 0) line else "\n$line"
        }.filter { it.isNotEmpty() }.toList()
        ChunkMode.PARAGRAPH -> markdown
            .split(Regex("(?<=\\n\\n)"))
            .filter { it.isNotEmpty() }
    }
}

private fun chunkByToken(markdown: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    for (char in markdown) {
        current.append(char)
        val shouldFlush = when {
            char == '\n' -> true
            char.isWhitespace() -> true
            current.length >= 10 -> true
            else -> false
        }
        if (shouldFlush) {
            result += current.toString()
            current.clear()
        }
    }
    if (current.isNotEmpty()) {
        result += current.toString()
    }
    return result
}

private fun buildStreamingSampleMarkdown(sectionCount: Int): String = buildString {
    append("# Kotlin Multiplatform Streaming Benchmark\n\n")
    append("这是一个用于评估 **Markdown** 流式渲染路径性能的基准样本。\n\n")
    append("> 目标：模拟 LLM 逐 chunk 输出时，解析与渲染准备链路的开销。\n\n")
    append("## 概览\n\n")
    append("- 支持标题、列表、表格、代码块、引用、数学公式\n")
    append("- 强调流式增量解析与 block 列表更新\n")
    append("- 适合回归比较不同优化前后的趋势\n\n")

    repeat(sectionCount) { index ->
        val number = index + 1
        append("## 第 $number 章：模块化与流式输出\n\n")
        append("在这一章中，我们讨论 **Kotlin Multiplatform**、增量解析、`Compose` 渲染，以及长文档分页策略。\n\n")
        append("### 关键点\n\n")
        append("- 解析器需要只重算尾部 dirty region\n")
        append("- 渲染器需要避免无意义的重组和协程重启\n")
        append("- 状态更新应优先走同步表达式而不是副作用\n\n")
        append("| 指标 | 含义 | 目标 |\n")
        append("|:---|:---|:---|\n")
        append("| append latency | 单次 chunk 追加耗时 | 越低越好 |\n")
        append("| finalize cost | 流结束收尾耗时 | 平稳可控 |\n")
        append("| block updates | 可见块列表变化次数 | 尽量少 |\n\n")
        append("```kotlin\n")
        append("fun streamChunk(index: Int): String {\n")
        append("    return \"chunk-\" + index\n")
        append("}\n")
        append("```\n\n")
        append("公式示例：\$E = mc^2\$，以及块公式：\n\n")
        append("$$\n")
        append("\\nabla \\cdot \\vec{E} = \\frac{\\rho}{\\varepsilon_0}\n")
        append("$$\n\n")
    }
}
