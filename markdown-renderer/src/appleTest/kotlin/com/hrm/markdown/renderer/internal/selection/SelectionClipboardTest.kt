package com.hrm.markdown.renderer.internal.selection

import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalComposeUiApi::class)
class SelectionClipboardTest {
    @Test
    fun should_preservePlainText_when_creatingAppleClipboardEntry() {
        val text = "正文 **原文**\nsecond line 🙂"
        val entry = assertNotNull(plainTextClipEntry(text))
        assertEquals(text, entry.getPlainText())
    }

    @Test
    fun should_preserveEmptyText_when_creatingAppleClipboardEntry() {
        assertEquals("", assertNotNull(plainTextClipEntry("")).getPlainText())
    }
}
