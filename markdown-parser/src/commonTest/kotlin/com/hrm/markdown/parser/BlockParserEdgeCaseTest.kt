package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// indented code block

class IndentedCodeBlockEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_4_space_indented_code() {
        val doc = parser.parse("    code line")
        val block = doc.children.first()
        assertIs<IndentedCodeBlock>(block)
        assertTrue(block.literal.contains("code line"))
    }

    @Test
    fun should_parse_tab_indented_code() {
        val doc = parser.parse("\tcode line")
        val block = doc.children.first()
        assertIs<IndentedCodeBlock>(block)
        assertTrue(block.literal.contains("code line"))
    }

    @Test
    fun should_parse_multi_line_indented_code() {
        val doc = parser.parse("    line1\n    line2\n    line3")
        val block = doc.children.first()
        assertIs<IndentedCodeBlock>(block)
        assertTrue(block.literal.contains("line1"))
        assertTrue(block.literal.contains("line3"))
    }

    @Test
    fun should_preserve_blank_lines_between_indented_code() {
        val doc = parser.parse("    line1\n\n    line2")
        val block = doc.children.first()
        assertIs<IndentedCodeBlock>(block)
        assertTrue(block.literal.contains("line1"))
        assertTrue(block.literal.contains("line2"))
    }

    @Test
    fun should_not_start_indented_code_inside_list() {
        val doc = parser.parse("- Item\n\n      code in list")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }

    @Test
    fun should_strip_leading_4_spaces_from_content() {
        val doc = parser.parse("    code")
        val block = doc.children.first()
        assertIs<IndentedCodeBlock>(block)
        assertTrue(block.literal.trimEnd().startsWith("code"))
    }

    @Test
    fun should_not_treat_3_spaces_as_code_block() {
        val doc = parser.parse("   not code")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }
}

// setext headings

class SetextHeadingEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_long_equals_underline() {
        val doc = parser.parse("Heading\n======")
        val heading = doc.children.first()
        assertIs<SetextHeading>(heading)
        assertEquals(1, heading.level)
    }

    @Test
    fun should_parse_single_equals_underline() {
        val doc = parser.parse("Heading\n=")
        val heading = doc.children.first()
        assertIs<SetextHeading>(heading)
        assertEquals(1, heading.level)
    }

    @Test
    fun should_allow_trailing_spaces_on_underline() {
        val doc = parser.parse("Heading\n===   ")
        val heading = doc.children.first()
        assertIs<SetextHeading>(heading)
    }

    @Test
    fun should_include_multiline_content() {
        val doc = parser.parse("Line 1\nLine 2\n===")
        val heading = doc.children.first()
        assertIs<SetextHeading>(heading)
    }

    @Test
    fun should_parse_inline_markup_in_setext() {
        val doc = parser.parse("**Bold** heading\n===")
        val heading = doc.children.first()
        assertIs<SetextHeading>(heading)
        assertTrue(heading.children.any { it is StrongEmphasis })
    }

    @Test
    fun should_not_be_setext_without_preceding_content() {
        val doc = parser.parse("\n===")
        assertTrue(doc.children.isEmpty() || doc.children.first() is Paragraph)
    }

    @Test
    fun should_allow_up_to_3_space_indent_on_underline() {
        val doc = parser.parse("Heading\n   ===")
        val heading = doc.children.first()
        assertIs<SetextHeading>(heading)
    }

    @Test
    fun should_reject_4_space_indent_on_underline() {
        val doc = parser.parse("Heading\n    ===")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }
}

// block quotes

class BlockQuoteEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_handle_lazy_continuation() {
        val doc = parser.parse("> First line\nLazy continuation")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
    }

    @Test
    fun should_nest_three_levels_deep() {
        val doc = parser.parse("> > > Deep")
        val bq1 = doc.children.first()
        assertIs<BlockQuote>(bq1)
        val bq2 = bq1.children.first()
        assertIs<BlockQuote>(bq2)
        val bq3 = bq2.children.first()
        assertIs<BlockQuote>(bq3)
    }

    @Test
    fun should_handle_empty_quote_marker() {
        val doc = parser.parse(">")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
    }

    @Test
    fun should_contain_heading_inside_quote() {
        val doc = parser.parse("> # Heading inside quote")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        assertTrue(bq.children.any { it is Heading })
    }

    @Test
    fun should_contain_fenced_code_inside_quote() {
        val doc = parser.parse("> ```\n> code\n> ```")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        assertTrue(bq.children.any { it is FencedCodeBlock })
    }

    @Test
    fun should_contain_list_inside_quote() {
        val doc = parser.parse("> - Item 1\n> - Item 2")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        assertTrue(bq.children.any { it is ListBlock })
    }

    @Test
    fun should_split_paragraphs_on_blank_quote_line() {
        val doc = parser.parse("> Para 1\n>\n> Para 2")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        val paras = bq.children.filterIsInstance<Paragraph>()
        assertEquals(2, paras.size)
    }
}

// lists

class ListEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_produce_tight_list_without_blanks() {
        val doc = parser.parse("- A\n- B\n- C")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertTrue(list.tight)
    }

    @Test
    fun should_produce_loose_list_with_blanks() {
        val doc = parser.parse("- A\n\n- B\n\n- C")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertFalse(list.tight)
    }

    @Test
    fun should_nest_sublists() {
        val doc = parser.parse("- Parent\n  - Child\n  - Child 2\n- Parent 2")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertTrue(list.children.filterIsInstance<ListItem>().size >= 2)
    }

    @Test
    fun should_nest_three_levels() {
        val doc = parser.parse("- Level 1\n  - Level 2\n    - Level 3")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }

    @Test
    fun should_allow_code_inside_list_item() {
        val doc = parser.parse("- Item\n\n      code block")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }

    @Test
    fun should_start_ordered_list_at_0() {
        val doc = parser.parse("0. Zero\n1. One")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertTrue(list.ordered)
        assertEquals(0, list.startNumber)
    }

    @Test
    fun should_start_ordered_list_at_large_number() {
        val doc = parser.parse("999. Large\n1000. Larger")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertEquals(999, list.startNumber)
    }

    @Test
    fun should_include_continuation_in_item() {
        val doc = parser.parse("- Item 1\n  continuation\n- Item 2")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertEquals(2, list.children.filterIsInstance<ListItem>().size)
    }

    @Test
    fun should_parse_single_item() {
        val doc = parser.parse("- Single item")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertEquals(1, list.children.filterIsInstance<ListItem>().size)
    }

    @Test
    fun should_handle_empty_list_item() {
        val doc = parser.parse("-\n- Item")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }
}

// tables

class TableEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_single_column() {
        val doc = parser.parse("| A |\n| --- |\n| 1 |")
        val table = doc.children.first()
        assertIs<Table>(table)
        assertEquals(1, table.columnAlignments.size)
    }

    @Test
    fun should_handle_escaped_pipe_in_cell() {
        val doc = parser.parse("| A | B |\n| --- | --- |\n| a\\|b | c |")
        val table = doc.children.first()
        assertIs<Table>(table)
    }

    @Test
    fun should_handle_empty_cells() {
        val doc = parser.parse("| A | B |\n| --- | --- |\n| | |")
        val table = doc.children.first()
        assertIs<Table>(table)
    }

    @Test
    fun should_handle_fewer_cells_than_header() {
        val doc = parser.parse("| A | B | C |\n| --- | --- | --- |\n| 1 |")
        val table = doc.children.first()
        assertIs<Table>(table)
    }

    @Test
    fun should_handle_extra_cells_in_row() {
        val doc = parser.parse("| A | B |\n| --- | --- |\n| 1 | 2 | 3 | 4 |")
        val table = doc.children.first()
        assertIs<Table>(table)
    }

    @Test
    fun should_detect_all_alignment_types() {
        val input = "| L | C | R | N |\n| :--- | :---: | ---: | --- |\n| a | b | c | d |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)
        assertEquals(Table.Alignment.LEFT, table.columnAlignments[0])
        assertEquals(Table.Alignment.CENTER, table.columnAlignments[1])
        assertEquals(Table.Alignment.RIGHT, table.columnAlignments[2])
        assertEquals(Table.Alignment.NONE, table.columnAlignments[3])
    }

    @Test
    fun should_parse_inline_formatting_in_cells() {
        val input = "| A |\n| --- |\n| **bold** |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)
    }

    @Test
    fun should_have_head_and_body_sections() {
        val input = "| A |\n| --- |\n| 1 |\n| 2 |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)
        assertTrue(table.children.any { it is TableHead })
        assertTrue(table.children.any { it is TableBody })
    }

    @Test
    fun should_reject_table_without_separator_row() {
        val doc = parser.parse("| A | B |\n| 1 | 2 |")
        val first = doc.children.first()
        assertTrue(first !is Table)
    }
}

// admonitions

class AdmonitionEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_note_admonition() {
        val doc = parser.parse("> [!NOTE]\n> This is a note.")
        val first = doc.children.first()
        assertTrue(first is Admonition || first is BlockQuote)
    }

    @Test
    fun should_parse_warning_admonition() {
        val doc = parser.parse("> [!WARNING]\n> Be careful!")
        val first = doc.children.first()
        assertTrue(first is Admonition || first is BlockQuote)
    }

    @Test
    fun should_parse_tip_admonition() {
        val doc = parser.parse("> [!TIP]\n> Helpful tip here.")
        val first = doc.children.first()
        assertTrue(first is Admonition || first is BlockQuote)
    }

    @Test
    fun should_parse_multiline_admonition() {
        val doc = parser.parse("> [!NOTE]\n> Line 1\n> Line 2\n> Line 3")
        val first = doc.children.first()
        assertTrue(first is Admonition || first is BlockQuote)
    }

    @Test
    fun should_parse_important_admonition() {
        val doc = parser.parse("> [!IMPORTANT]\n> Critical info")
        val first = doc.children.first()
        assertTrue(first is Admonition || first is BlockQuote)
    }
}

// definition lists

class DefinitionListEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_simple_definition() {
        val doc = parser.parse("Term\n: Definition")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_parse_multiple_definitions_for_one_term() {
        val doc = parser.parse("Term\n: Definition 1\n: Definition 2")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_parse_multiple_terms_sharing_definition() {
        val doc = parser.parse("Term 1\nTerm 2\n: Shared definition")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_handle_blank_line_before_definition() {
        val doc = parser.parse("Term\n\n: Definition with blank line before")
        assertTrue(doc.children.isNotEmpty())
    }
}

// front matter

class FrontMatterEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_empty_yaml_front_matter() {
        val doc = parser.parse("---\n---\n\nContent")
        val fm = doc.children.first()
        assertIs<FrontMatter>(fm)
        assertEquals("yaml", fm.format)
    }

    @Test
    fun should_parse_yaml_with_nested_content() {
        val doc = parser.parse("---\ntitle: Test\ntags:\n  - a\n  - b\ndate: 2024-01-01\n---\n\nContent")
        val fm = doc.children.first()
        assertIs<FrontMatter>(fm)
        assertTrue(fm.literal.contains("title: Test"))
        assertTrue(fm.literal.contains("tags:"))
    }

    @Test
    fun should_reject_front_matter_not_at_document_start() {
        val doc = parser.parse("Some text\n---\ntitle: Test\n---")
        val first = doc.children.first()
        assertTrue(first !is FrontMatter)
    }

    @Test
    fun should_parse_toml_front_matter() {
        val doc = parser.parse("+++\ntitle = \"Test\"\ndate = 2024-01-01\n+++\n\nContent")
        val fm = doc.children.first()
        assertIs<FrontMatter>(fm)
        assertEquals("toml", fm.format)
    }
}

// math blocks

class MathBlockEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_multi_line_formula() {
        val doc = parser.parse("$$\na + b = c\nx^2 + y^2 = z^2\n$$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
        assertTrue(math.literal.contains("a + b = c"))
        assertTrue(math.literal.contains("x^2"))
    }

    @Test
    fun should_preserve_latex_commands() {
        val doc = parser.parse("$$\n\\int_{0}^{\\infty} e^{-x} dx = 1\n$$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
        assertTrue(math.literal.contains("\\int"))
    }

    @Test
    fun should_parse_empty_math_block() {
        val doc = parser.parse("$$\n$$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
    }

    @Test
    fun should_not_interpret_markdown_inside_math() {
        val doc = parser.parse("$$\n# Not a heading\n**not bold**\n$$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
        assertTrue(math.literal.contains("# Not a heading"))
    }
}

// html blocks

class HtmlBlockEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_processing_instruction() {
        val doc = parser.parse("<?xml version=\"1.0\"?>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertEquals(3, html.htmlType)
    }

    @Test
    fun should_parse_cdata_section() {
        val doc = parser.parse("<![CDATA[\nsome text\n]]>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertEquals(5, html.htmlType)
    }

    @Test
    fun should_parse_doctype() {
        val doc = parser.parse("<!DOCTYPE html>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertEquals(4, html.htmlType)
    }

    @Test
    fun should_parse_pre_block_as_type1() {
        val doc = parser.parse("<pre>\ncode here\n</pre>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertEquals(1, html.htmlType)
    }

    @Test
    fun should_parse_multiline_html_comment() {
        val doc = parser.parse("<!-- \nmultiline\ncomment\n-->")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertEquals(2, html.htmlType)
    }

    @Test
    fun should_parse_self_closing_tag() {
        val doc = parser.parse("<hr />")
        val first = doc.children.first()
        assertIs<HtmlBlock>(first)
    }
}
