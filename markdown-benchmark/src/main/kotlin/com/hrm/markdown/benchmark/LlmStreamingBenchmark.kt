package com.hrm.markdown.benchmark

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Node
import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * LLM 场景流式基准。
 *
 * 模拟「LLM 边出 token 边渲染」的真实负载：
 *   - 把一份 ~50KB 的混合 Markdown（段落/标题/代码块/表格/列表/链接/数学）
 *     按"约 1 token 每 10ms"的速率分成若干 chunk
 *   - 测量每次 [MarkdownParser.append] 的耗时分布与遍历整棵 AST 的耗时
 *
 * 这是 LLM 应用展示场景下最关键的稳定性 + 性能指标：
 *   - p50/p95/p99 append 必须远低于一帧（16ms），否则 UI 掉帧
 *   - 总 append 耗时 vs 一次性 parse 的开销比，反映"流式税"
 *   - traverse 耗时反映"延迟行内解析 + 后处理"的成本
 *
 * 使用：
 *   ./gradlew :markdown-benchmark:llmStreamingBenchmark \
 *       --args="--bytes 50000 --tokenSize 4 --warmups 3 --iterations 5"
 */
private const val DEFAULT_BYTES = 50_000
private const val DEFAULT_TOKEN_SIZE = 4   // 平均每 chunk 4 字符 ≈ 1 BPE token
private const val DEFAULT_WARMUPS = 3
private const val DEFAULT_ITERATIONS = 5

fun main(args: Array<String>) {
    val cli = LlmCli.parse(args)
    val doc = if (cli.codeHeavy) buildCodeHeavyDocument(cli.bytes) else buildLlmLikeDocument(cli.bytes)
    val chunks = chunkLikeLlm(doc, cli.tokenSize, Random(0xA17BAB1EL))

    println("LLM Streaming Benchmark${if (cli.codeHeavy) " [CODE-HEAVY]" else ""}")
    println("docBytes=${doc.length}, chunks=${chunks.size}, avgChunk=${doc.length / chunks.size}")
    if (cli.coalesce > 0) {
        println("appendCoalesceThreshold=${cli.coalesce} chars (parser-level)")
    }
    println("warmups=${cli.warmups}, iterations=${cli.iterations}")
    println()

    repeat(cli.warmups) { runOneIteration(chunks, cli.coalesce) }

    val results = Array(cli.iterations) { runOneIteration(chunks, cli.coalesce) }

    val appendNs = results.flatMap { it.appendNs.toList() }.sorted()
    fun pct(p: Double): Long = appendNs[((appendNs.size - 1) * p).toInt()]
    val appendAvg = appendNs.average()

    val totalStreamMs = results.map { it.totalStreamNs / 1_000_000.0 }.average()
    val traverseMs = results.map { it.traverseNs / 1_000_000.0 }.average()
    val oneShotMs = results.map { it.oneShotNs / 1_000_000.0 }.average()
    val tax = totalStreamMs / oneShotMs

    println("Per-append latency (over ${appendNs.size} appends)")
    println("  avg=${(appendAvg / 1_000.0).fmt()} us")
    println("  p50=${(pct(0.50) / 1_000.0).fmt()} us")
    println("  p95=${(pct(0.95) / 1_000.0).fmt()} us")
    println("  p99=${(pct(0.99) / 1_000.0).fmt()} us")
    println("  max=${(appendNs.last() / 1_000.0).fmt()} us")
    println()
    println("Throughput")
    println("  total streaming = ${totalStreamMs.fmt()} ms  (incl. ${chunks.size} appends)")
    println("  one-shot parse  = ${oneShotMs.fmt()} ms")
    println("  streaming tax   = ${tax.fmt()}x")
    println("  AST traverse    = ${traverseMs.fmt()} ms (post-stream)")
    println()
    val frameBudgetUs = 16_000.0
    val overBudget = appendNs.count { it / 1_000.0 > frameBudgetUs }
    val pctOver = overBudget * 100.0 / appendNs.size
    println("Frame-budget check (16ms)")
    println("  appends over 16ms = $overBudget / ${appendNs.size}  (${pctOver.fmt()}%)")
}

private data class LlmIterationResult(
    val appendNs: LongArray,
    val totalStreamNs: Long,
    val traverseNs: Long,
    val oneShotNs: Long,
)

private fun runOneIteration(chunks: List<String>, coalesce: Int = 0): LlmIterationResult {
    val parser = MarkdownParser(appendCoalesceThreshold = coalesce)
    val appendNs = LongArray(chunks.size)
    lateinit var finalDoc: Node
    val total = measureNanoTime {
        for ((i, c) in chunks.withIndex()) {
            appendNs[i] = measureNanoTime { parser.append(c) }
        }
        finalDoc = parser.endStream()
    }
    val traverse = measureNanoTime { traverseAll(finalDoc) }

    // One-shot baseline on a fresh parser.
    val full = chunks.joinToString("")
    val oneShot = measureNanoTime { MarkdownParser().parse(full) }
    return LlmIterationResult(appendNs, total, traverse, oneShot)
}

private fun traverseAll(node: Node) {
    if (node is ContainerNode) {
        for (c in node.children) traverseAll(c)
    }
}

private data class LlmCli(
    val bytes: Int,
    val tokenSize: Int,
    val warmups: Int,
    val iterations: Int,
    val coalesce: Int,
    val codeHeavy: Boolean,
) {
    companion object {
        fun parse(args: Array<String>): LlmCli {
            var bytes = DEFAULT_BYTES
            var tokenSize = DEFAULT_TOKEN_SIZE
            var warmups = DEFAULT_WARMUPS
            var iterations = DEFAULT_ITERATIONS
            var coalesce = 0
            var codeHeavy = false
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--bytes" -> { bytes = args.getOrNull(i + 1)?.toIntOrNull() ?: bytes; i++ }
                    "--tokenSize" -> { tokenSize = args.getOrNull(i + 1)?.toIntOrNull() ?: tokenSize; i++ }
                    "--warmups" -> { warmups = args.getOrNull(i + 1)?.toIntOrNull() ?: warmups; i++ }
                    "--iterations" -> { iterations = args.getOrNull(i + 1)?.toIntOrNull() ?: iterations; i++ }
                    "--coalesce" -> { coalesce = args.getOrNull(i + 1)?.toIntOrNull() ?: coalesce; i++ }
                    "--codeBlockHeavy" -> codeHeavy = true
                }
                i++
            }
            return LlmCli(
                bytes.coerceAtLeast(1024),
                tokenSize.coerceAtLeast(1),
                warmups.coerceAtLeast(0),
                iterations.coerceAtLeast(1),
                coalesce.coerceAtLeast(0),
                codeHeavy,
            )
        }
    }
}

private fun Double.fmt(): String = "%.3f".format(this)

/**
 * Code-heavy 文档：模拟编程助手场景。约 70% 字节是大段代码块（每块 30~80 行）。
 * 用于验证 IncrementalEngine 对未闭合 FencedCodeBlock 的实例复用 + 增量解析路径
 * 在 LLM 大代码生成场景下不会退化。
 */
private fun buildCodeHeavyDocument(targetBytes: Int): String = buildString(targetBytes + 1024) {
    var section = 0
    while (length < targetBytes) {
        section++
        append("## Code example ").append(section).append("\n\n")
        append("Here is implementation ").append(section).append(":\n\n")
        append("```kotlin\n")
        val lines = 30 + (section * 7) % 50
        repeat(lines) { i ->
            append("fun method").append(section).append('_').append(i)
                .append("(x: Int, y: Long): String {\n")
            append("    val result = x.toLong() * y + ").append(i).append("L\n")
            append("    val list = (0..x).map { it * ").append(i + 1).append(" }\n")
            append("    return \"section=").append(section).append(" idx=").append(i).append(" v=\$result\"\n")
            append("}\n")
        }
        append("```\n\n")
        append("That was section ").append(section).append(".\n\n")
    }
}

/**
 * 构造一篇约 [targetBytes] 字节的 LLM 风格混合 Markdown。
 * 包含：标题、段落、列表、表格、行内代码、围栏代码块、链接、行内/块级数学。
 */
private fun buildLlmLikeDocument(targetBytes: Int): String = buildString(targetBytes + 1024) {
    var section = 0
    while (length < targetBytes) {
        section++
        append("## Section ").append(section).append(": Topic ").append(section).append('\n')
        append('\n')
        append("Here is a paragraph about *topic ").append(section)
            .append("* with some **emphasis** and `inline code` plus a [link](https://example.com/")
            .append(section).append("). It also references `foo()` and `bar(x, y)`.\n")
        append('\n')
        append("- bullet item ").append(section).append("-1 with **bold**\n")
        append("- bullet item ").append(section).append("-2 with [a link](https://example.com)\n")
        append("- bullet item ").append(section).append("-3 with `inline code`\n")
        append('\n')
        append("```kotlin\n")
        append("fun example").append(section).append("(x: Int): Int {\n")
        append("    val sum = (0..x).sum()\n")
        append("    println(\"sum=\$sum\")\n")
        append("    return sum\n")
        append("}\n")
        append("```\n")
        append('\n')
        append("| col A | col B | col C |\n")
        append("| ----- | ----- | ----- |\n")
        append("| a").append(section).append(" | b").append(section).append(" | c").append(section).append(" |\n")
        append("| x").append(section).append(" | y").append(section).append(" | z").append(section).append(" |\n")
        append('\n')
        append("> A blockquote line referencing section ").append(section).append(".\n")
        append('\n')
        append("Inline math like \$E = mc^2\$ appears in section ").append(section).append(".\n")
        append('\n')
        append("\$\$\n")
        append("\\sum_{i=0}^{").append(section).append("} i^2 = \\frac{n(n+1)(2n+1)}{6}\n")
        append("\$\$\n")
        append('\n')
    }
}

/**
 * 已废弃：保留以备测试对比。优先使用 parser 自带的 [MarkdownParser.appendCoalesceThreshold]。
 */
@Suppress("unused")
private fun coalesceChunks(chunks: List<String>, threshold: Int): List<String> {
    val out = ArrayList<String>(chunks.size)
    val buf = StringBuilder()
    for (c in chunks) {
        buf.append(c)
        val containsNewline = c.indexOf('\n') >= 0
        if (containsNewline || buf.length >= threshold) {
            out.add(buf.toString())
            buf.setLength(0)
        }
    }
    if (buf.isNotEmpty()) out.add(buf.toString())
    return out
}

private fun chunkLikeLlm(text: String, tokenSize: Int, rng: Random): List<String> {
    val out = ArrayList<String>(text.length / tokenSize + 16)
    var pos = 0
    while (pos < text.length) {
        val r = rng.nextDouble()
        val size = when {
            r < 0.6 -> rng.nextInt(1, tokenSize + 1)
            r < 0.9 -> rng.nextInt(tokenSize, tokenSize * 4 + 1)
            else -> rng.nextInt(tokenSize * 4, tokenSize * 16 + 1)
        }
        val end = (pos + size).coerceAtMost(text.length)
        out.add(text.substring(pos, end))
        pos = end
    }
    return out
}
