package com.hrm.markdown.renderer.internal.layout.inline

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
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
}
