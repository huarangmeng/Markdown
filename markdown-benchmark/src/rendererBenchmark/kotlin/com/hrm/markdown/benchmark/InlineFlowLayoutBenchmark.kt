package com.hrm.markdown.benchmark

import com.hrm.markdown.renderer.internal.layout.inline.InlineFlowInput
import com.hrm.markdown.renderer.internal.layout.inline.InlineFlowLayout
import com.hrm.markdown.renderer.internal.layout.inline.InlineFlowMeasurementStats
import com.hrm.markdown.renderer.internal.layout.inline.InlineFlowSegment
import com.hrm.markdown.renderer.internal.layout.inline.InlinePlaceholderLayoutSpec
import com.hrm.markdown.renderer.internal.layout.inline.computeInlineFlowLayout

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.hrm.markdown.renderer.inline.InlinePlaceholderId
import kotlin.time.TimeSource

/** Opt-in wall-clock measurements; deterministic work budgets live in InlineFlowLongTextTest. */
fun main(args: Array<String>) {
    val maxLength = args.firstOrNull()?.toIntOrNull() ?: 32_000
    val density = Density(1f, 1f)
    val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val resolver = createFontFamilyResolver()
    fun compute(input: InlineFlowInput, stats: InlineFlowMeasurementStats? = null): InlineFlowLayout =
        computeInlineFlowLayout(
            input, style, density, TextMeasurer(resolver, density, LayoutDirection.Ltr, cacheSize = 8),
            maxWidthPx = 260f, maxLines = Int.MAX_VALUE, measurementStats = stats,
        )
    repeat(5) { compute(benchmarkInput(1000, mixed = false)) }
    println("# revision=${System.getProperty("markdown.benchmark.revision", "working-tree")}")
    println("# java=${System.getProperty("java.runtime.version")}; os=${System.getProperty("os.name")}; arch=${System.getProperty("os.arch")}")
    println("# warmups=5x1000; iterations=3; width_px=260; font_sp=16; density=1; font_scale=1; heap=256/512MiB")
    println("# Includes fresh TextMeasurer construction and flow layout; excludes input/resolver setup, parsing, LaTeX measurement and painting.")
    println("# mixed_placeholders uses fixed 96x36 formula and 40x24 component specs, not live LaTeX rendering.")
    println("scenario,characters,lines,median_ms,measure_calls,measured_characters")
    for (mixed in listOf(false, true)) {
        for (length in listOf(1000, 2000, 4000, 8000, 16_000, 32_000).filter { it <= maxLength }) {
            val input = benchmarkInput(length, mixed)
            val samples = ArrayList<Double>()
            var result: InlineFlowLayout? = null
            var stats = InlineFlowMeasurementStats()
            repeat(3) {
                stats = InlineFlowMeasurementStats()
                val start = TimeSource.Monotonic.markNow()
                result = compute(input, stats)
                samples += start.elapsedNow().inWholeNanoseconds / 1_000_000.0
                benchmarkSink = result
            }
            samples.sort()
            println("${if (mixed) "mixed_placeholders" else "text"},$length,${checkNotNull(result).lines.size},${samples[1]},${stats.calls},${stats.characters}")
        }
    }
}

@Volatile
private var benchmarkSink: InlineFlowLayout? = null

private fun benchmarkInput(length: Int, mixed: Boolean): InlineFlowInput {
    val unit = "这是一段用于验证超长单段落换行性能的中文文本abcdefghij"
    val value = unit.repeat((length + unit.length - 1) / unit.length).take(length)
    if (!mixed) return InlineFlowInput(listOf(InlineFlowSegment.TextRun(AnnotatedString(value), 0, length)))
    val middle = length / 2
    return InlineFlowInput(listOf(
        InlineFlowSegment.TextRun(AnnotatedString(value.take(middle)), 0, middle),
        InlineFlowSegment.InlineRun(InlinePlaceholderId(1), InlinePlaceholderLayoutSpec("formula", 96f, 36f), middle, middle + 7),
        InlineFlowSegment.TextRun(AnnotatedString(value.drop(middle)), middle + 7, length + 7),
        InlineFlowSegment.InlineRun(InlinePlaceholderId(2), InlinePlaceholderLayoutSpec("component", 40f, 24f), length + 7, length + 16),
    ))
}
