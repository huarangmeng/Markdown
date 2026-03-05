package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration tests that verify complex, real-world Markdown documents.
 */
class IntegrationTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_complex_document() {
        val input = """
            # Title

            This is a paragraph with **bold** and *italic* text.

            ## Code Example

            ```kotlin
            fun main() {
                println("Hello, World!")
            }
            ```

            ## Lists

            - Item 1
            - Item 2
              - Nested item
            - Item 3

            1. First
            2. Second
            3. Third

            ---

            > This is a block quote.
            > It has multiple lines.

            | Header 1 | Header 2 |
            | -------- | -------- |
            | Cell 1   | Cell 2   |
        """.trimIndent()

        val doc = parser.parse(input)
        assertTrue(doc.children.isNotEmpty())

        // Verify headings
        val headings = doc.children.filterIsInstance<Heading>()
        assertTrue(headings.isNotEmpty())

        // Verify code block
        val codeBlocks = doc.children.filterIsInstance<FencedCodeBlock>()
        assertTrue(codeBlocks.isNotEmpty())
        assertEquals("kotlin", codeBlocks.first().language)

        // Verify lists
        val lists = doc.children.filterIsInstance<ListBlock>()
        assertTrue(lists.isNotEmpty())

        // Verify thematic break
        val breaks = doc.children.filterIsInstance<ThematicBreak>()
        assertTrue(breaks.isNotEmpty())

        // Verify block quote
        val quotes = doc.children.filterIsInstance<BlockQuote>()
        assertTrue(quotes.isNotEmpty())

        // Verify table
        val tables = doc.children.filterIsInstance<Table>()
        assertTrue(tables.isNotEmpty())
    }

    @Test
    fun should_parse_inline_rich_text() {
        val doc = parser.parse("**bold** and *italic* and `code` and ~~strike~~")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is StrongEmphasis })
        assertTrue(para.children.any { it is Emphasis })
        assertTrue(para.children.any { it is InlineCode })
        assertTrue(para.children.any { it is Strikethrough })
    }

    @Test
    fun should_parse_nested_structures() {
        val input = """
            > # Heading in quote
            >
            > - List in quote
            > - Item 2
            >
            > ```
            > code in quote
            > ```
        """.trimIndent()

        val doc = parser.parse(input)
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        assertTrue(bq.children.isNotEmpty())
    }

    @Test
    fun should_parse_links_and_images_mixed() {
        val doc = parser.parse("Visit [Google](https://google.com) and see ![logo](logo.png)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Link })
        assertTrue(para.children.any { it is Image })
    }

    @Test
    fun should_handle_edge_case_empty_elements() {
        val doc = parser.parse("# \n\n## \n\n---\n\n```\n```")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_handle_deeply_nested_quotes() {
        val doc = parser.parse(">>> Deep")
        val bq1 = doc.children.first()
        assertIs<BlockQuote>(bq1)
        val bq2 = bq1.children.first()
        assertIs<BlockQuote>(bq2)
    }

    @Test
    fun should_handle_special_characters_in_code() {
        val doc = parser.parse("```\n<div class=\"test\">&amp;</div>\n```")
        val code = doc.children.first()
        assertIs<FencedCodeBlock>(code)
        assertTrue(code.literal.contains("<div"))
    }

    @Test
    fun should_parse_link_reference_definition() {
        val input = "[example]: https://example.com \"Example\"\n\nClick [here][example]."
        val doc = parser.parse(input)
        assertTrue(doc.linkDefinitions.isNotEmpty())
    }

    @Test
    fun should_parse_footnote() {
        val input = "Text with footnote[^1].\n\n[^1]: This is the footnote."
        val doc = parser.parse(input)
        // Should have footnote definition
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_parse_task_list_in_context() {
        val input = """
            ## TODO

            - [x] Task 1
            - [ ] Task 2
            - [x] Task 3
        """.trimIndent()

        val doc = parser.parse(input)
        val list = doc.children.filterIsInstance<ListBlock>().first()
        val items = list.children.filterIsInstance<ListItem>()
        assertEquals(3, items.size)
        assertTrue(items[0].checked)
        assertTrue(!items[1].checked)
        assertTrue(items[2].checked)
    }

    @Test
    fun should_maintain_line_ranges() {
        val input = "# Title\n\nParagraph\n\n## Subtitle"
        val doc = parser.parse(input)
        for (child in doc.children) {
            assertTrue(child.lineRange.lineCount > 0,
                "Node ${child::class.simpleName} should have valid line range")
        }
    }

    @Test
    fun should_handle_unicode_text() {
        val doc = parser.parse("# 你好世界\n\n这是一个**粗体**的段落。")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        val para = doc.children.last()
        assertIs<Paragraph>(para)
    }

    @Test
    fun should_parse_admonition_like_syntax() {
        val doc = parser.parse("> [!NOTE]\n> This is a note.")
        val admonition = doc.children.first()
        assertIs<Admonition>(admonition)
    }

    @Test
    fun should_parse_definition_list_marker() {
        // Definition lists are extension syntax
        val doc = parser.parse("Term\n: Definition")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_parse_highlight_syntax() {
        val doc = parser.parse("This is ==highlighted== text")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Highlight })
    }
}
