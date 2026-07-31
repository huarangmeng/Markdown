package com.hrm.markdown.renderer.internal.layout.inline

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InlineFlowLayoutEngineTest {
    @Test
    fun should_wrap_before_next_text_run_when_first_cjk_glyph_does_not_fit_remaining_width() = runComposeUiTest {
        var layout: InlineFlowLayout? = null
        var maxWidthPx = 0f

        setContent {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val style = TextStyle(fontSize = 16.sp)
            val prefixWidth = textMeasurer.measure(
                text = "ab",
                style = textMeasurementStyle(style),
                constraints = Constraints(maxWidth = Int.MAX_VALUE),
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat()
            maxWidthPx = prefixWidth + 1f
            layout = computeInlineFlowLayout(
                input = InlineFlowInput(
                    listOf(
                        InlineFlowSegment.TextRun(AnnotatedString("ab")),
                        InlineFlowSegment.TextRun(AnnotatedString("中文标点，继续")),
                    )
                ),
                style = style,
                density = density,
                textMeasurer = textMeasurer,
                maxWidthPx = maxWidthPx,
                maxLines = Int.MAX_VALUE,
            )
        }

        waitForIdle()

        val actual = assertNotNull(layout)
        assertTrue(actual.lines.size > 1, "Expected long mixed text to wrap in a narrow inline container.")
        actual.assertNoLineOrRunExceeds(maxWidthPx)
    }

    @Test
    fun should_emit_emergency_single_glyph_run_when_no_prefix_fits() = runComposeUiTest {
        var layout: InlineFlowLayout? = null
        val maxWidthPx = 1f

        setContent {
            layout = computeInlineFlowLayout(
                input = InlineFlowInput(
                    listOf(
                        InlineFlowSegment.TextRun(AnnotatedString("宽")),
                    )
                ),
                style = TextStyle(fontSize = 16.sp),
                density = LocalDensity.current,
                textMeasurer = rememberTextMeasurer(),
                maxWidthPx = maxWidthPx,
                maxLines = Int.MAX_VALUE,
            )
        }

        waitForIdle()

        val actual = assertNotNull(layout)
        assertEquals(1, actual.lines.size)
        assertEquals(1, actual.lines.first().items.size)
        assertTrue(actual.lines.first().items.first().widthPx > 0f)
    }

    @Test
    fun should_wrap_long_url_within_available_width() = runComposeUiTest {
        var layout: InlineFlowLayout? = null
        val maxWidthPx = 260f

        setContent {
            layout = computeInlineFlowLayout(
                input = InlineFlowInput(
                    listOf(
                        InlineFlowSegment.TextRun(
                            AnnotatedString("[1] ↩︎ An exact analytical solution of Kepler's Equation[https://link.springer.com/article/10.1007/BF01231473]")
                        ),
                    )
                ),
                style = TextStyle(fontSize = 16.sp),
                density = LocalDensity.current,
                textMeasurer = rememberTextMeasurer(),
                maxWidthPx = maxWidthPx,
                maxLines = Int.MAX_VALUE,
            )
        }

        waitForIdle()

        val actual = assertNotNull(layout)
        assertTrue(actual.lines.size > 1, "Expected long URL text to wrap across multiple lines.")
        actual.assertNoLineOrRunExceeds(maxWidthPx)
    }

    @Test
    fun should_keep_devanagari_text_inside_line_when_line_height_is_specified() = runComposeUiTest {
        var flowLayout: InlineFlowLayout? = null

        setContent {
            flowLayout = computeInlineFlowLayout(
                input = InlineFlowInput(
                    listOf(
                        InlineFlowSegment.TextRun(AnnotatedString("भीमसेन थापा")),
                    )
                ),
                style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                density = LocalDensity.current,
                textMeasurer = rememberTextMeasurer(),
                maxWidthPx = 320f,
                maxLines = Int.MAX_VALUE,
            )
        }

        waitForIdle()

        val actual = assertNotNull(flowLayout)
        val layoutLines = buildInlineLayoutLines(
            identity = renderIdentityForTest,
            contentLeft = 0f,
            contentTop = 12f,
            layout = actual,
            widgetById = emptyMap(),
        )
        val flowLine = actual.lines.single()
        val textItem = flowLine.items.single() as LineItem.TextItem
        val layoutLine = layoutLines.single()
        val textRun = layoutLine.runs.single()

        assertTrue(textRun.frame.top >= layoutLine.frame.top)
        assertTrue(
            textRun.frame.top + textRun.frame.height <= layoutLine.frame.top + layoutLine.frame.height
        )
        assertEquals(
            expected = layoutLine.baseline,
            actual = textRun.frame.top + textItem.baselinePx,
            absoluteTolerance = 0.01f,
        )
    }

    @Test
    fun should_align_mixed_text_runs_to_one_baseline_without_clipping() = runComposeUiTest {
        var flowLayout: InlineFlowLayout? = null

        setContent {
            flowLayout = computeInlineFlowLayout(
                input = InlineFlowInput(
                    listOf(
                        InlineFlowSegment.TextRun(AnnotatedString("Latin")),
                        InlineFlowSegment.TextRun(
                            AnnotatedString(
                                text = "पृथ्वीनारायण",
                                spanStyles = listOf(
                                    AnnotatedString.Range(
                                        item = SpanStyle(fontSize = 22.sp),
                                        start = 0,
                                        end = "पृथ्वीनारायण".length,
                                    )
                                ),
                            )
                        ),
                    )
                ),
                style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                density = LocalDensity.current,
                textMeasurer = rememberTextMeasurer(),
                maxWidthPx = 500f,
                maxLines = Int.MAX_VALUE,
            )
        }

        waitForIdle()

        val actual = assertNotNull(flowLayout)
        val flowLine = actual.lines.single()
        val layoutLine = buildInlineLayoutLines(
            identity = renderIdentityForTest,
            contentLeft = 0f,
            contentTop = 20f,
            layout = actual,
            widgetById = emptyMap(),
        ).single()
        val textItems = flowLine.items.filterIsInstance<LineItem.TextItem>()

        assertEquals(textItems.size, layoutLine.runs.size)
        layoutLine.runs.zip(textItems).forEach { (run, item) ->
            assertTrue(run.frame.top >= layoutLine.frame.top)
            assertTrue(run.frame.top + run.frame.height <= layoutLine.frame.top + layoutLine.frame.height)
            assertEquals(
                expected = layoutLine.baseline,
                actual = run.frame.top + item.baselinePx,
                absoluteTolerance = 0.01f,
            )
        }
    }

    @Test
    fun should_preserve_explicit_source_ranges_when_text_wraps() = runComposeUiTest {
        var flowLayout: InlineFlowLayout? = null

        setContent {
            flowLayout = computeInlineFlowLayout(
                input = InlineFlowInput(
                    listOf(
                        InlineFlowSegment.TextRun(
                            annotated = AnnotatedString("alpha beta gamma"),
                            sourceStart = 7,
                            sourceEnd = 23,
                        ),
                    )
                ),
                style = TextStyle(fontSize = 16.sp),
                density = LocalDensity.current,
                textMeasurer = rememberTextMeasurer(),
                maxWidthPx = 55f,
                maxLines = Int.MAX_VALUE,
            )
        }

        waitForIdle()

        val items = assertNotNull(flowLayout).lines
            .flatMap { it.items }
            .filterIsInstance<LineItem.TextItem>()
        assertTrue(items.size > 1)
        items.forEach { item ->
            assertEquals(item.text.length, checkNotNull(item.sourceEnd) - checkNotNull(item.sourceStart))
        }
        assertEquals(7, items.first().sourceStart)
        assertEquals(23, items.last().sourceEnd)
    }

    private fun InlineFlowLayout.assertNoLineOrRunExceeds(maxWidthPx: Float) {
        val epsilon = 0.5f
        lines.forEachIndexed { lineIndex, line ->
            assertTrue(
                actual = line.lineWidthPx <= maxWidthPx + epsilon,
                message = "Line $lineIndex width ${line.lineWidthPx} exceeded max width $maxWidthPx.",
            )
            var cursor = 0f
            line.items.forEachIndexed { itemIndex, item ->
                val right = cursor + item.widthPx
                assertTrue(
                    actual = right <= maxWidthPx + epsilon,
                    message = "Line $lineIndex item $itemIndex right edge $right exceeded max width $maxWidthPx.",
                )
                cursor = right
            }
        }
    }

    private companion object {
        val renderIdentityForTest = RenderIdentity(
            stableId = 1L,
            contentRevision = 1L,
            layoutRevision = 1L,
            paintRevision = 1L,
        )
    }
}
