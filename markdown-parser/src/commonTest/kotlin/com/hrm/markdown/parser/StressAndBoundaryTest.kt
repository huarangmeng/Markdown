package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// large documents — stress tests for parser stability

class LargeDocumentTest {

    private val parser = MarkdownParser()

    @Test
    fun should_handle_10k_character_paragraph() {
        val text = "x".repeat(10_000)
        val doc = parser.parse(text)
        val para = doc.children.first()
        assertIs<Paragraph>(para)
    }

    @Test
    fun should_handle_100_headings() {
        val input = (1..100).joinToString("\n\n") { "# Heading $it" }
        val doc = parser.parse(input)
        val headings = doc.children.filterIsInstance<Heading>()
        assertEquals(100, headings.size)
    }

    @Test
    fun should_handle_deep_blockquote_nesting() {
        val prefix = "> ".repeat(10)
        val doc = parser.parse("${prefix}Deep content")
        var node: Node = doc.children.first()
        var depth = 0
        while (node is BlockQuote) {
            depth++
            node = node.children.firstOrNull() ?: break
        }
        assertTrue(depth >= 5) // at least 5 levels should be created
    }

    @Test
    fun should_handle_deep_list_nesting() {
        val lines = (0 until 8).map { level ->
            " ".repeat(level * 2) + "- Level ${level + 1}"
        }
        val doc = parser.parse(lines.joinToString("\n"))
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }

    @Test
    fun should_handle_many_inline_elements() {
        val parts = (1..50).map { "**bold$it** *italic$it* `code$it`" }
        val doc = parser.parse(parts.joinToString(" "))
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.size > 100)
    }

    @Test
    fun should_handle_large_table() {
        val header = (1..20).joinToString(" | ") { "H$it" }
        val sep = (1..20).joinToString(" | ") { "---" }
        val rows = (1..50).joinToString("\n") { r ->
            (1..20).joinToString(" | ") { c -> "R${r}C$c" }
        }
        val input = "| $header |\n| $sep |\n| ${rows.replace("\n", " |\n| ")} |"
        val doc = parser.parse(input)
        assertTrue(doc.children.any { it is Table })
    }
}

// boundary cases — unusual inputs

class BoundaryInputTest {

    private val parser = MarkdownParser()

    @Test
    fun should_handle_empty_input() {
        val doc = parser.parse("")
        assertTrue(doc.children.isEmpty())
    }

    @Test
    fun should_handle_only_whitespace() {
        val doc = parser.parse("   \n   \n   ")
        assertTrue(doc.children.isEmpty())
    }

    @Test
    fun should_handle_only_newlines() {
        val doc = parser.parse("\n\n\n\n\n")
        assertTrue(doc.children.isEmpty())
    }

    @Test
    fun should_handle_crlf_line_endings() {
        val doc = parser.parse("# Title\r\n\r\nParagraph\r\n")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
    }

    @Test
    fun should_handle_cr_line_endings() {
        val doc = parser.parse("# Title\r\rParagraph")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
    }

    @Test
    fun should_replace_null_chars() {
        val doc = parser.parse("hello\u0000world")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val text = para.children.first()
        assertIs<Text>(text)
        assertTrue(text.literal.contains("\uFFFD"))
    }

    @Test
    fun should_handle_unicode_content() {
        val doc = parser.parse("# 你好世界\n\nПривет мир\n\n🎉 emoji text")
        assertTrue(doc.children.size >= 2)
        val heading = doc.children.first()
        assertIs<Heading>(heading)
    }

    @Test
    fun should_handle_single_character_input() {
        val doc = parser.parse("a")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
    }

    @Test
    fun should_handle_very_long_heading() {
        val title = "x".repeat(1000)
        val doc = parser.parse("# $title")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
    }

    @Test
    fun should_handle_mixed_line_endings() {
        val doc = parser.parse("line1\nline2\r\nline3\rline4")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_handle_trailing_spaces_on_last_line() {
        val doc = parser.parse("hello   ")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
    }

    @Test
    fun should_handle_document_with_only_thematic_break() {
        val doc = parser.parse("---")
        val tb = doc.children.first()
        assertIs<ThematicBreak>(tb)
    }

    @Test
    fun should_handle_many_blank_lines_between_blocks() {
        val doc = parser.parse("# Title\n\n\n\n\n\n\n\nParagraph")
        assertTrue(doc.children.filterIsInstance<Heading>().isNotEmpty())
        assertTrue(doc.children.filterIsInstance<Paragraph>().isNotEmpty())
    }
}
