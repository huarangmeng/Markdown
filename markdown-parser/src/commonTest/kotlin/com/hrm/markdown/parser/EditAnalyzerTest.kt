package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.incremental.EditAnalyzer
import com.hrm.markdown.parser.incremental.EditOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// edit operations — data class behavior

class EditOperationTest {

    @Test
    fun should_create_insert_operation() {
        val op = EditOperation.Insert(offset = 5, text = "hello")
        assertEquals(5, op.offset)
        assertEquals("hello", op.text)
    }

    @Test
    fun should_create_delete_operation() {
        val op = EditOperation.Delete(startOffset = 3, endOffset = 10)
        assertEquals(3, op.startOffset)
        assertEquals(10, op.endOffset)
    }

    @Test
    fun should_create_replace_operation() {
        val op = EditOperation.Replace(startOffset = 0, endOffset = 5, newText = "world")
        assertEquals(0, op.startOffset)
        assertEquals(5, op.endOffset)
        assertEquals("world", op.newText)
    }

    @Test
    fun should_support_data_class_equality() {
        val a = EditOperation.Insert(5, "hi")
        val b = EditOperation.Insert(5, "hi")
        assertEquals(a, b)
    }

    @Test
    fun should_support_data_class_copy() {
        val original = EditOperation.Delete(0, 10)
        val modified = original.copy(endOffset = 20)
        assertEquals(20, modified.endOffset)
        assertEquals(0, modified.startOffset)
    }
}

// edit analyzer — determines dirty lines and deltas after edits

class EditAnalyzerAnalysisTest {

    private fun lineOffsetsOf(text: String): IntArray {
        val offsets = mutableListOf(0)
        for (i in text.indices) {
            if (text[i] == '\n' && i + 1 <= text.length) {
                offsets.add(i + 1)
            }
        }
        return offsets.toIntArray()
    }

    @Test
    fun should_analyze_single_line_insert() {
        val text = "hello world"
        val offsets = lineOffsetsOf(text)
        val result = EditAnalyzer.analyze(text, offsets, EditOperation.Insert(5, " beautiful"))
        assertEquals(0, result.lineDelta)
        assertEquals(10, result.offsetDelta) // " beautiful".length
    }

    @Test
    fun should_analyze_insert_with_newlines() {
        val text = "hello world"
        val offsets = lineOffsetsOf(text)
        val result = EditAnalyzer.analyze(text, offsets, EditOperation.Insert(5, "\n\n"))
        assertEquals(2, result.lineDelta)
        assertEquals(2, result.offsetDelta)
    }

    @Test
    fun should_analyze_delete_single_line() {
        val text = "hello world"
        val offsets = lineOffsetsOf(text)
        val result = EditAnalyzer.analyze(text, offsets, EditOperation.Delete(5, 11))
        assertEquals(0, result.lineDelta)
        assertEquals(-6, result.offsetDelta)
    }

    @Test
    fun should_analyze_delete_across_lines() {
        val text = "line1\nline2\nline3"
        val offsets = lineOffsetsOf(text)
        val result = EditAnalyzer.analyze(text, offsets, EditOperation.Delete(3, 9))
        assertEquals(-1, result.lineDelta)
    }

    @Test
    fun should_analyze_replace_same_length() {
        val text = "hello world"
        val offsets = lineOffsetsOf(text)
        val result = EditAnalyzer.analyze(text, offsets, EditOperation.Replace(0, 5, "HELLO"))
        assertEquals(0, result.lineDelta)
        assertEquals(0, result.offsetDelta)
    }

    @Test
    fun should_analyze_replace_adding_lines() {
        val text = "hello"
        val offsets = lineOffsetsOf(text)
        val result = EditAnalyzer.analyze(text, offsets, EditOperation.Replace(0, 5, "a\nb\nc"))
        assertEquals(2, result.lineDelta) // 0 newlines removed, 2 added
    }

    @Test
    fun should_analyze_replace_removing_lines() {
        val text = "line1\nline2\nline3"
        val offsets = lineOffsetsOf(text)
        val result = EditAnalyzer.analyze(text, offsets, EditOperation.Replace(0, 17, "single"))
        assertEquals(-2, result.lineDelta)
    }

    @Test
    fun should_report_dirty_range_covering_affected_lines() {
        val text = "line1\nline2\nline3"
        val offsets = lineOffsetsOf(text)
        val result = EditAnalyzer.analyze(text, offsets, EditOperation.Insert(8, "X"))
        assertTrue(result.dirtyRange.contains(1))
    }
}

// incremental parsing — applying edits through MarkdownParser

class IncrementalEditIntegrationTest {

    private val parser = MarkdownParser()

    @Test
    fun should_update_heading_text_on_insert() {
        parser.parse("# Hello\n\nWorld")
        val doc = parser.applyEdit(EditOperation.Insert(7, " Kotlin"))
        assertTrue(doc.children.isNotEmpty())
        assertIs<Heading>(doc.children.first())
    }

    @Test
    fun should_add_new_block_on_insert() {
        parser.parse("# Hello")
        val doc = parser.applyEdit(EditOperation.Insert(7, "\n\nNew paragraph"))
        assertTrue(doc.children.size >= 2)
    }

    @Test
    fun should_remove_block_on_delete() {
        parser.parse("# Hello\n\nParagraph")
        val doc = parser.applyEdit(EditOperation.Delete(0, 8))
        assertTrue(doc.children.none { it is Heading })
    }

    @Test
    fun should_change_block_type_on_replace() {
        parser.parse("Paragraph text")
        val doc = parser.applyEdit(EditOperation.Replace(0, 14, "# Now a heading"))
        assertIs<Heading>(doc.children.first())
    }

    @Test
    fun should_handle_batch_edits() {
        parser.parse("# Hello\n\nWorld\n\nFoo")
        val doc = parser.applyEdits(listOf(
            EditOperation.Insert(14, " changed"),
            EditOperation.Replace(2, 7, "Bye")
        ))
        assertTrue(doc.children.isNotEmpty())
    }
}
