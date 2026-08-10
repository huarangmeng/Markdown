package com.hrm.markdown.renderer.internal.layout.engine

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.hrm.markdown.renderer.internal.core.model.BlockTextAlignment
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockTextAlignmentTest {
    @Test
    fun should_map_alignment_when_render_model_overrides_text_style() {
        assertEquals(
            TextAlign.Start,
            TextStyle.Default.withBlockTextAlignment(BlockTextAlignment.START).textAlign,
        )
        assertEquals(
            TextAlign.Center,
            TextStyle.Default.withBlockTextAlignment(BlockTextAlignment.CENTER).textAlign,
        )
        assertEquals(
            TextAlign.End,
            TextStyle.Default.withBlockTextAlignment(BlockTextAlignment.END).textAlign,
        )
    }

    @Test
    fun should_preserve_theme_alignment_when_render_model_inherits() {
        val themed = TextStyle.Default.copy(textAlign = TextAlign.Justify)

        assertEquals(themed, themed.withBlockTextAlignment(BlockTextAlignment.INHERIT))
    }
}
