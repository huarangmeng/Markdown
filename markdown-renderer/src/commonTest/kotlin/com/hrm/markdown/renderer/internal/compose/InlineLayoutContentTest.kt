package com.hrm.markdown.renderer.internal.compose

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.layout.inline.InlineFlowInput
import com.hrm.markdown.renderer.internal.layout.inline.InlineFlowSegment
import com.hrm.markdown.renderer.internal.layout.inline.buildInlineLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.inline.computeInlineFlowLayout
import com.hrm.markdown.renderer.internal.layout.model.LayoutRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class InlineLayoutContentTest {
    @Test
    fun should_not_clip_devanagari_when_line_height_is_specified() = runComposeUiTest {
        var textLayoutResult: TextLayoutResult? = null

        setContent {
            val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val flowLayout = computeInlineFlowLayout(
                input = InlineFlowInput(
                    listOf(
                        InlineFlowSegment.TextRun(AnnotatedString("भीमसेन थापा")),
                    )
                ),
                style = style,
                density = density,
                textMeasurer = textMeasurer,
                maxWidthPx = 320f,
                maxLines = Int.MAX_VALUE,
            )
            val block = buildInlineLayoutBlockModel(
                identity = renderIdentityForTest,
                frame = LayoutRect(0f, 0f, 320f, flowLayout.heightPx),
                contentFrame = LayoutRect(0f, 0f, 320f, flowLayout.heightPx),
                style = style,
                layout = flowLayout,
                inlinePayloads = emptyMap(),
                widgetById = emptyMap(),
            )

            PaintInlineLayoutContent(
                block = block,
                onTextLayout = { textLayoutResult = it },
            )
        }

        waitForIdle()

        val actual = assertNotNull(textLayoutResult)
        assertEquals(TextUnit.Unspecified, actual.layoutInput.style.lineHeight)
        assertFalse(actual.hasVisualOverflow)
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
