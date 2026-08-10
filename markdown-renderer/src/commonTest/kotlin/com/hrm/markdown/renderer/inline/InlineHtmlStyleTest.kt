package com.hrm.markdown.renderer.inline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.internal.core.model.SpanMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InlineHtmlStyleTest {
    @Test
    fun should_merge_class_and_inline_styles_with_inline_style_precedence() {
        val style = spanStyleForMark(
            mark = SpanMark(
                kind = "styled",
                payload = mapOf(
                    "class" to "bold italic blue",
                    "style" to "color:red",
                ),
            ),
            theme = MarkdownTheme(),
        )

        assertEquals(Color.Red, style.color)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(FontStyle.Italic, style.fontStyle)
    }

    @Test
    fun should_parse_css_eight_digit_hex_as_rrggbbaa() {
        val color = assertNotNull(parseCssColor("#ff000080"))

        assertEquals(1f, color.red, absoluteTolerance = 0.001f)
        assertEquals(0f, color.green, absoluteTolerance = 0.001f)
        assertEquals(0f, color.blue, absoluteTolerance = 0.001f)
        assertEquals(128f / 255f, color.alpha, absoluteTolerance = 0.001f)
    }
}
