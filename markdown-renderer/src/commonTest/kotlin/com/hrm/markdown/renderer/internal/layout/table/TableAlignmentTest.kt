package com.hrm.markdown.renderer.internal.layout.table

import androidx.compose.ui.text.style.TextAlign
import com.hrm.markdown.parser.ast.Table
import kotlin.test.Test
import kotlin.test.assertEquals

class TableAlignmentTest {
    @Test
    fun should_keep_explicit_leftAndRight_physical() {
        assertEquals(TextAlign.Left, tableTextAlign(Table.Alignment.LEFT))
        assertEquals(TextAlign.Right, tableTextAlign(Table.Alignment.RIGHT))
    }

    @Test
    fun should_use_logicalStart_when_alignmentIsUnspecified() {
        assertEquals(TextAlign.Start, tableTextAlign(Table.Alignment.NONE))
    }
}
