package com.hrm.markdown.renderer.internal.layout.inline

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class InlineLayoutAlignmentTest {
    @Test
    fun should_keep_physical_alignment_independent_of_layout_direction() {
        assertEquals(0f, offset(TextAlign.Left, LayoutDirection.Ltr))
        assertEquals(0f, offset(TextAlign.Left, LayoutDirection.Rtl))
        assertEquals(80f, offset(TextAlign.Right, LayoutDirection.Ltr))
        assertEquals(80f, offset(TextAlign.Right, LayoutDirection.Rtl))
    }

    @Test
    fun should_resolve_logical_alignment_from_layout_direction() {
        assertEquals(0f, offset(TextAlign.Start, LayoutDirection.Ltr))
        assertEquals(80f, offset(TextAlign.Start, LayoutDirection.Rtl))
        assertEquals(80f, offset(TextAlign.End, LayoutDirection.Ltr))
        assertEquals(0f, offset(TextAlign.End, LayoutDirection.Rtl))
    }

    private fun offset(alignment: TextAlign, direction: LayoutDirection): Float =
        resolveInlineLineStartOffset(
            textAlign = alignment,
            remainingWidth = 80f,
            layoutDirection = direction,
        )
}
