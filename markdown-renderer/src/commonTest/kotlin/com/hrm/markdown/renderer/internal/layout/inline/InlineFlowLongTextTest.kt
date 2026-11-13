package com.hrm.markdown.renderer.internal.layout.inline

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.hrm.markdown.renderer.inline.InlinePlaceholderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InlineFlowLongTextTest {
    @Test
    fun should_advance_binary_search_when_midpoint_is_inside_a_surrogate_pair() {
        for (text in listOf("a😀", "a😀a😀a😀", "😀😀", "abc😀def😀", "😀")) {
            val boundaries = (0..text.length).filter { safeTextBreakIndex(text, it) == it }
            for (width in 0..text.length) {
                var probes = 0
                val actual = fittingTextPrefixLength(text, width.toFloat()) { end ->
                    assertTrue(++probes <= 16, "Search must terminate even if a safe midpoint precedes lo")
                    assertTrue(end in boundaries)
                    end.toFloat()
                }
                assertEquals(boundaries.last { it <= width }, actual, "text=$text width=$width")
            }
        }
    }

    @Test
    fun should_finish_narrow_fractional_layout_without_losing_or_splitting_emoji() = withMeasurer { measurer, density ->
        val text = AnnotatedString("a😀a😀a😀")
        val aWidth = measurer.measure("a", style = style, softWrap = false).size.width
        val layout = layout(text, measurer, density, width = aWidth + 0.75f)
        assertTextPreserved(text, layout)
        assertTrue(layout.lines.size > 1)
        layout.textItems().forEach { item ->
            assertTrue(!item.text.first().isLowSurrogate())
            assertTrue(!item.text.last().isHighSurrogate())
        }
    }

    @Test
    fun should_match_native_wraps_and_preserve_styles_when_text_crosses_measurement_windows() = withMeasurer { measurer, density ->
        val value = "中".repeat(1023) + "😀" + "连续中文标点，混合abcdefghij😀".repeat(400)
        val text = AnnotatedString(value, spanStyles = listOf(
            AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 1000, 1100),
            AnnotatedString.Range(SpanStyle(fontSize = 22.sp), 3100, 3300),
        ))
        val actual = layout(text, measurer, density)
        val native = measurer.measure(
            text, style = textMeasurementStyle(style), constraints = Constraints(maxWidth = 260),
            skipCache = true,
        )
        val expectedLines = (0 until native.lineCount).map { index ->
            text.subSequence(native.getLineStart(index), native.getLineEnd(index, visibleEnd = false))
        }
        assertEquals(expectedLines, actual.lines.map { line -> line.items.single().let { (it as LineItem.TextItem).text } })
        assertTextPreserved(text, actual)
        assertFits(actual, 260f)
    }

    @Test
    fun should_keep_sources_and_baselines_when_long_runs_surround_inline_objects() = withMeasurer { measurer, density ->
        val before = AnnotatedString("正文😀混合abcdef".repeat(800))
        val after = AnnotatedString("后文继续😀abcdefgh".repeat(800))
        val inlineStart = before.length
        val afterStart = inlineStart + 4
        val wideStart = afterStart + after.length
        val segments = listOf(
            InlineFlowSegment.TextRun(before, 0, before.length),
            inline(1, 47.5f, 52f, inlineStart, inlineStart + 4),
            InlineFlowSegment.TextRun(after, afterStart, wideStart),
            inline(2, 900f, 40f, wideStart, wideStart + 3),
            InlineFlowSegment.TextRun(AnnotatedString("末尾"), wideStart + 3, wideStart + 5),
        )
        val actual = computeInlineFlowLayout(InlineFlowInput(segments), style, density, measurer, 260f, Int.MAX_VALUE)
        val objects = actual.lines.flatMap { it.items }.filterIsInstance<LineItem.InlineItem>()
        assertEquals(listOf(InlinePlaceholderId(1), InlinePlaceholderId(2)), objects.map { it.id })
        assertEquals(47.5f, objects[0].widthPx)
        assertEquals(260f, objects[1].widthPx)
        assertEquals(900f, objects[1].naturalWidthPx)
        assertEquals(listOf(inlineStart, wideStart), objects.map { it.sourceStart })
        assertEquals(before.text + after.text + "末尾", actual.textItems().joinToString("") { it.text.text })
        assertFits(actual, 260f)
        val textSource = before.text + "公式占位" + after.text + "组件占" + "末尾"
        actual.textItems().forEach { item ->
            assertEquals(item.text.text, textSource.substring(checkNotNull(item.sourceStart), checkNotNull(item.sourceEnd)))
        }
        actual.lines.forEach { line ->
            line.items.filterIsInstance<LineItem.TextItem>().forEach { item ->
                val top = line.baselinePx - item.baselinePx
                assertTrue(top >= -0.01f && top + item.heightPx <= line.lineHeightPx + 0.01f)
            }
        }
    }

    @Test
    fun should_preserve_word_boundaries_and_source_gaps_when_wrapping_whitespace() = withMeasurer { measurer, density ->
        val text = AnnotatedString("alpha beta   gamma\tdelta ".repeat(500) + "END")
        val actual = layout(text, measurer, density)
        val items = actual.textItems()
        items.forEach { item ->
            assertEquals(text.subSequence(checkNotNull(item.sourceStart), checkNotNull(item.sourceEnd)), item.text)
        }
        items.zipWithNext().forEach { (left, right) ->
            val skipped = text.text.substring(checkNotNull(left.sourceEnd), checkNotNull(right.sourceStart))
            assertTrue(skipped.all { it.isWhitespace() }, "Only wrap-leading whitespace may be skipped")
        }
        assertEquals(text.length, items.last().sourceEnd)
        assertFits(actual, 260f)
    }

    @Test
    fun should_bound_measurement_work_when_long_paragraphs_grow_with_or_without_widgets() = withMeasurer { measurer, density ->
        for (mixed in listOf(false, true)) {
            var previousWork = 0L
            for (length in listOf(8_000, 16_000, 32_000)) {
                val text = AnnotatedString("连续中文😀abcdefghij".repeat(length).take(length))
                val segments = if (mixed) listOf(
                    inline(1, 30f, 45f, 0, 2),
                    InlineFlowSegment.TextRun(text, 2, 2 + text.length),
                    inline(2, 20f, 30f, 2 + text.length, 4 + text.length),
                ) else listOf(InlineFlowSegment.TextRun(text, 0, text.length))
                val stats = InlineFlowMeasurementStats()
                val actual = computeInlineFlowLayout(InlineFlowInput(segments), style, density, measurer, 260f, Int.MAX_VALUE, stats)
                assertEquals(text.text, actual.textItems().joinToString("") { it.text.text })
                assertTrue(stats.characters <= length * 6L + 8192, "Expected linear measurement work: length=$length measured=${stats.characters}")
                if (previousWork > 0) assertTrue(stats.characters < previousWork * 3, "Doubling input must not quadruple measured text")
                previousWork = stats.characters
            }
        }
    }

    @Test
    fun should_stop_work_at_max_lines_and_keep_explicit_newlines_after_a_long_run() = withMeasurer { measurer, density ->
        val text = AnnotatedString("中文😀abcdefghij".repeat(10_000))
        val stats = InlineFlowMeasurementStats()
        val truncated = layout(text, measurer, density, maxLines = 2, stats = stats)
        assertEquals(2, truncated.lines.size)
        assertTrue(stats.characters < 4096, "maxLines must not measure the complete long tail")
        val short = AnnotatedString("中文".repeat(1000))
        val actual = computeInlineFlowLayout(InlineFlowInput(listOf(
            InlineFlowSegment.TextRun(short), InlineFlowSegment.Newline, InlineFlowSegment.Newline,
            InlineFlowSegment.TextRun(AnnotatedString("END")),
        )), style, density, measurer, 260f, Int.MAX_VALUE)
        assertTrue(actual.lines[actual.lines.lastIndex - 1].items.isEmpty())
        assertEquals("END", actual.textItems().last().text.text)
    }

    @Test
    fun should_extend_a_window_without_inserting_breaks_when_a_whole_long_run_fits() = withMeasurer { measurer, density ->
        val text = AnnotatedString("abc".repeat(6000))
        val stats = InlineFlowMeasurementStats()
        val actual = layout(text, measurer, density, width = 200_000f, stats = stats)
        assertEquals(1, actual.lines.size)
        assertTextPreserved(text, actual)
        assertTrue(stats.characters < text.length * 4L)
    }

    private fun layout(
        text: AnnotatedString, measurer: TextMeasurer, density: Density,
        width: Float = 260f, maxLines: Int = Int.MAX_VALUE, stats: InlineFlowMeasurementStats? = null,
    ) = computeInlineFlowLayout(
        InlineFlowInput(listOf(InlineFlowSegment.TextRun(text, 0, text.length))),
        style, density, measurer, width, maxLines, stats,
    )

    private fun inline(id: Long, width: Float, height: Float, start: Int, end: Int) = InlineFlowSegment.InlineRun(
        InlinePlaceholderId(id), InlinePlaceholderLayoutSpec("widget", width, height), start, end,
    )

    private fun InlineFlowLayout.textItems() = lines.flatMap { it.items }.filterIsInstance<LineItem.TextItem>()

    private fun assertTextPreserved(text: AnnotatedString, actual: InlineFlowLayout) {
        assertEquals(text.text, actual.textItems().joinToString("") { it.text.text })
        actual.textItems().forEach { item ->
            assertEquals(text.subSequence(checkNotNull(item.sourceStart), checkNotNull(item.sourceEnd)), item.text)
        }
    }

    private fun assertFits(actual: InlineFlowLayout, width: Float) {
        actual.lines.forEach { assertTrue(it.lineWidthPx <= width + 0.01f, "Line width ${it.lineWidthPx} > $width") }
    }

    private fun withMeasurer(block: (TextMeasurer, Density) -> Unit) = runComposeUiTest {
        lateinit var measurer: TextMeasurer
        lateinit var density: Density
        setContent { measurer = rememberTextMeasurer(); density = LocalDensity.current }
        runOnIdle { block(measurer, density) }
    }

    private val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
}
