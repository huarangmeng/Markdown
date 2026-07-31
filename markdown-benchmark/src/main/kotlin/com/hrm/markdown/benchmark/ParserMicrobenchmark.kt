package com.hrm.markdown.benchmark

import com.hrm.markdown.parser.MarkdownParser
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import org.openjdk.jmh.annotations.Fork
import java.util.concurrent.TimeUnit

/**
 * Forked JVM parser microbenchmarks backed by JMH through kotlinx-benchmark.
 *
 * Keep scenario construction outside benchmark methods so reported scores describe parser work,
 * while each method creates fresh mutable parser state to match production ownership semantics.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
open class ParserMicrobenchmark {
    private lateinit var mixedDocument: String
    private lateinit var inlineHeavyDocument: String
    private lateinit var streamingChunks: List<String>

    @Setup
    open fun setUp() {
        mixedDocument = buildMicrobenchmarkDocument(sectionCount = 80)
        inlineHeavyDocument = buildInlineHeavyDocument(paragraphCount = 200)
        streamingChunks = mixedDocument.chunked(8)
    }

    @Benchmark
    open fun fullParse() = MarkdownParser().parse(mixedDocument)

    @Benchmark
    open fun inlineHeavyParse() = MarkdownParser().parse(inlineHeavyDocument)

    @Benchmark
    open fun streamingAppendBatch() = MarkdownParser(appendCoalesceThreshold = 16).run {
        beginStream()
        streamingChunks.forEach(::append)
        endStream()
    }
}

private fun buildMicrobenchmarkDocument(sectionCount: Int): String = buildString {
    repeat(sectionCount) { section ->
        append("## Section ").append(section).append('\n').append('\n')
        append("Paragraph with **bold**, *italic*, `code`, and ")
        append("[link](https://example.com/").append(section).append(").\n\n")
        append("- item one\n- item two\n- item three\n\n")
        append("```kotlin\nfun section").append(section).append("() = ").append(section).append("\n```\n\n")
        append("| A | B |\n|---|---|\n| ").append(section).append(" | value |\n\n")
    }
}

private fun buildInlineHeavyDocument(paragraphCount: Int): String = buildString {
    repeat(paragraphCount) { paragraph ->
        append("**Kotlin** _Multiplatform_ `parser` ~~benchmark~~ ")
        append("[link](https://example.com/").append(paragraph).append(") ")
        append("https://github.com/example/repository :smile: repeated inline content.")
        append("\n\n")
    }
}
