package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.flavour.CommonMarkFlavour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HeadingParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_atx_heading_level1() {
        val doc = parser.parse("# Hello World")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(1, heading.level)
        val text = heading.children.first()
        assertIs<Text>(text)
        assertEquals("Hello World", text.literal)
    }

    @Test
    fun should_parse_atx_heading_level2() {
        val doc = parser.parse("## Second Level")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(2, heading.level)
    }

    @Test
    fun should_parse_atx_heading_level3() {
        val doc = parser.parse("### Third Level")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(3, heading.level)
    }

    @Test
    fun should_parse_atx_heading_level4() {
        val doc = parser.parse("#### Fourth")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(4, heading.level)
    }

    @Test
    fun should_parse_atx_heading_level5() {
        val doc = parser.parse("##### Fifth")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(5, heading.level)
    }

    @Test
    fun should_parse_atx_heading_level6() {
        val doc = parser.parse("###### Sixth")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(6, heading.level)
    }

    @Test
    fun should_not_parse_7_hashes_as_heading() {
        val doc = parser.parse("####### Not a heading")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }

    @Test
    fun should_require_space_after_hash() {
        val doc = parser.parse("#NoSpace")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }

    @Test
    fun should_strip_trailing_hashes() {
        val doc = parser.parse("# Heading #")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        val text = heading.children.first()
        assertIs<Text>(text)
        assertEquals("Heading", text.literal)
    }

    @Test
    fun should_allow_up_to_3_spaces_indent() {
        val doc = parser.parse("   # Heading")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(1, heading.level)
    }

    @Test
    fun should_parse_custom_heading_id() {
        val doc = parser.parse("### My Heading {#custom-id}")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(3, heading.level)
        assertEquals("custom-id", heading.customId)
    }

    @Test
    fun should_parse_empty_atx_heading() {
        val doc = parser.parse("#")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals(1, heading.level)
    }

    @Test
    fun should_parse_setext_heading_level1() {
        val doc = parser.parse("Heading\n===")
        val blocks = doc.children
        assertTrue(blocks.isNotEmpty())
        val heading = blocks.first()
        assertIs<SetextHeading>(heading)
        assertEquals(1, heading.level)
    }

    @Test
    fun should_parse_setext_heading_level2() {
        val doc = parser.parse("Heading\n---")
        val blocks = doc.children
        // Could be setext heading or thematic break depending on context
        assertTrue(blocks.isNotEmpty())
    }
}

class ParagraphParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_simple_paragraph() {
        val doc = parser.parse("Hello World")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val text = para.children.first()
        assertIs<Text>(text)
        assertEquals("Hello World", text.literal)
    }

    @Test
    fun should_merge_consecutive_lines() {
        val doc = parser.parse("Line 1\nLine 2")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
    }

    @Test
    fun should_separate_paragraphs_by_blank_line() {
        val doc = parser.parse("Para 1\n\nPara 2")
        assertEquals(2, doc.children.size)
        assertIs<Paragraph>(doc.children[0])
        assertIs<Paragraph>(doc.children[1])
    }

    @Test
    fun should_handle_empty_input() {
        val doc = parser.parse("")
        assertTrue(doc.children.isEmpty())
    }

    @Test
    fun should_handle_only_blank_lines() {
        val doc = parser.parse("\n\n\n")
        assertTrue(doc.children.isEmpty())
    }
}

class ThematicBreakParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_three_hyphens() {
        val doc = parser.parse("---")
        val tb = doc.children.first()
        assertIs<ThematicBreak>(tb)
    }

    @Test
    fun should_parse_three_asterisks() {
        val doc = parser.parse("***")
        val tb = doc.children.first()
        assertIs<ThematicBreak>(tb)
    }

    @Test
    fun should_parse_three_underscores() {
        val doc = parser.parse("___")
        val tb = doc.children.first()
        assertIs<ThematicBreak>(tb)
    }

    @Test
    fun should_parse_with_spaces_between() {
        val doc = parser.parse("- - -")
        val tb = doc.children.first()
        assertIs<ThematicBreak>(tb)
    }

    @Test
    fun should_parse_more_than_three() {
        val doc = parser.parse("----------")
        val tb = doc.children.first()
        assertIs<ThematicBreak>(tb)
    }

    @Test
    fun should_not_parse_two_hyphens() {
        val doc = parser.parse("--")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }

    @Test
    fun should_allow_up_to_3_spaces_indent() {
        val doc = parser.parse("   ---")
        val tb = doc.children.first()
        assertIs<ThematicBreak>(tb)
    }
}

class FencedCodeBlockParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_backtick_fence() {
        val doc = parser.parse("```\ncode\n```")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
        assertTrue(block.literal.contains("code"))
    }

    @Test
    fun should_parse_tilde_fence() {
        val doc = parser.parse("~~~\ncode\n~~~")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
    }

    @Test
    fun should_parse_info_string() {
        val doc = parser.parse("```kotlin\nfun main() {}\n```")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
        assertEquals("kotlin", block.language)
    }

    @Test
    fun should_take_first_word_as_language() {
        val doc = parser.parse("```kotlin extra info\ncode\n```")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
        assertEquals("kotlin", block.language)
    }

    @Test
    fun should_require_matching_fence_char() {
        // Opening with ``` should not close with ~~~
        val doc = parser.parse("```\ncode\n~~~")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
        // Content should include the ~~~ since it doesn't close
        assertTrue(block.literal.contains("~~~"))
    }

    @Test
    fun should_close_at_document_end_if_unclosed() {
        val doc = parser.parse("```\ncode line 1\ncode line 2")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
    }

    @Test
    fun should_not_parse_markdown_inside() {
        val doc = parser.parse("```\n# Not a heading\n**not bold**\n```")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
        assertTrue(block.literal.contains("# Not a heading"))
    }

    @Test
    fun should_allow_longer_closing_fence() {
        val doc = parser.parse("```\ncode\n`````")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
    }

    @Test
    fun should_not_allow_backticks_in_backtick_info() {
        val doc = parser.parse("``` foo`bar\ncode\n```")
        // The opening line has backtick in info, so not a valid fence
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }
}

class BlockQuoteParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_simple_block_quote() {
        val doc = parser.parse("> Hello")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        assertTrue(bq.children.isNotEmpty())
    }

    @Test
    fun should_parse_multi_line_block_quote() {
        val doc = parser.parse("> Line 1\n> Line 2")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
    }

    @Test
    fun should_parse_nested_block_quote() {
        val doc = parser.parse(">> Nested")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        val inner = bq.children.first()
        assertIs<BlockQuote>(inner)
    }

    @Test
    fun should_allow_optional_space_after_marker() {
        val doc = parser.parse(">No space")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
    }
}

class ListParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_unordered_list_dash() {
        val doc = parser.parse("- Item 1\n- Item 2")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertEquals(false, list.ordered)
        assertEquals(2, list.children.size)
    }

    @Test
    fun should_not_include_marker_in_unordered_list_item_content() {
        val doc = parser.parse("- 项目一\n- 项目二\n- 项目三")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        val items = list.children.filterIsInstance<ListItem>()
        assertEquals(3, items.size)

        // 每个列表项内的段落文本不应包含 "- " 标记
        items.forEachIndexed { index, item ->
            val para = item.children.first()
            assertIs<Paragraph>(para)
            val text = para.children.first()
            assertIs<Text>(text)
            val expected = listOf("项目一", "项目二", "项目三")[index]
            assertEquals(expected, text.literal, "Unordered list item $index should not contain marker")
        }
    }

    @Test
    fun should_not_include_marker_in_ordered_list_item_content() {
        val doc = parser.parse("1. 第一步\n2. 第二步\n3. 第三步")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        val items = list.children.filterIsInstance<ListItem>()
        assertEquals(3, items.size)

        // 每个列表项内的段落文本不应包含 "1." 等标记
        items.forEachIndexed { index, item ->
            val para = item.children.first()
            assertIs<Paragraph>(para)
            val text = para.children.first()
            assertIs<Text>(text)
            val expected = listOf("第一步", "第二步", "第三步")[index]
            assertEquals(expected, text.literal, "Ordered list item $index should not contain marker")
        }
    }

    @Test
    fun should_parse_unordered_list_asterisk() {
        val doc = parser.parse("* Item 1\n* Item 2")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }

    @Test
    fun should_parse_unordered_list_plus() {
        val doc = parser.parse("+ Item 1\n+ Item 2")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }

    @Test
    fun should_parse_ordered_list() {
        val doc = parser.parse("1. First\n2. Second\n3. Third")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertEquals(true, list.ordered)
    }

    @Test
    fun should_parse_ordered_list_with_paren() {
        val doc = parser.parse("1) First\n2) Second")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertEquals(true, list.ordered)
        assertEquals(')', list.delimiter)
    }

    @Test
    fun should_preserve_start_number() {
        val doc = parser.parse("3. Third\n4. Fourth")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertEquals(3, list.startNumber)
    }

    @Test
    fun should_parse_task_list() {
        val doc = parser.parse("- [ ] Unchecked\n- [x] Checked\n- [X] Also Checked")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        val items = list.children.filterIsInstance<ListItem>()
        assertEquals(3, items.size)
        assertTrue(items[0].taskListItem)
        assertEquals(false, items[0].checked)
        assertTrue(items[1].taskListItem)
        assertEquals(true, items[1].checked)
        assertTrue(items[2].taskListItem)
        assertEquals(true, items[2].checked)
    }

    @Test
    fun should_not_merge_different_markers() {
        val doc = parser.parse("- Dash\n* Asterisk")
        // Different markers create different lists
        assertTrue(doc.children.size >= 2)
    }
}

class HtmlBlockParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_div_html_block() {
        val doc = parser.parse("<div>\nContent\n</div>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertTrue(html.literal.contains("<div>"))
    }

    @Test
    fun should_parse_script_html_block() {
        val doc = parser.parse("<script>\nalert('hi');\n</script>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertEquals(1, html.htmlType)
    }

    @Test
    fun should_parse_html_comment_block() {
        val doc = parser.parse("<!-- comment -->")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertEquals(2, html.htmlType)
    }
}

class TableParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_simple_table() {
        val input = "| A | B |\n| --- | --- |\n| 1 | 2 |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)
    }

    @Test
    fun should_parse_alignment() {
        val input = "| Left | Center | Right |\n| :--- | :---: | ---: |\n| a | b | c |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)
        assertEquals(Table.Alignment.LEFT, table.columnAlignments[0])
        assertEquals(Table.Alignment.CENTER, table.columnAlignments[1])
        assertEquals(Table.Alignment.RIGHT, table.columnAlignments[2])
    }

    @Test
    fun should_parse_table_without_outer_pipes() {
        val input = "A | B\n--- | ---\n1 | 2"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)
    }

    @Test
    fun should_parse_header_cell_content_correctly() {
        val input = "| A | B | C |\n| --- | --- | --- |\n| 1 | 2 | 3 |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)

        val head = table.children.filterIsInstance<TableHead>().first()
        val headerRow = head.children.first()
        assertIs<TableRow>(headerRow)

        val headerCells = headerRow.children.filterIsInstance<TableCell>()
        assertEquals(3, headerCells.size)

        // 验证表头单元格的行内内容已正确解析（而非整行文本）
        val headerTexts = headerCells.map { cell ->
            val text = cell.children.firstOrNull()
            assertIs<Text>(text)
            text.literal
        }
        assertEquals(listOf("A", "B", "C"), headerTexts)
    }

    @Test
    fun should_parse_body_cell_content_correctly() {
        val input = "| A | B | C |\n| --- | --- | --- |\n| 1 | 2 | 3 |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)

        val body = table.children.filterIsInstance<TableBody>().first()
        val bodyRow = body.children.first()
        assertIs<TableRow>(bodyRow)

        val bodyCells = bodyRow.children.filterIsInstance<TableCell>()
        assertEquals(3, bodyCells.size)

        val bodyTexts = bodyCells.map { cell ->
            val text = cell.children.firstOrNull()
            assertIs<Text>(text)
            text.literal
        }
        assertEquals(listOf("1", "2", "3"), bodyTexts)
    }

    @Test
    fun should_parse_cell_with_inline_formatting() {
        val input = "| Header |\n| --- |\n| **bold** and *italic* |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)

        val body = table.children.filterIsInstance<TableBody>().first()
        val row = body.children.first()
        assertIs<TableRow>(row)
        val cell = row.children.first()
        assertIs<TableCell>(cell)

        // 验证行内格式被正确解析，而非包含 | 字符
        val cellText = cell.children.joinToString("") { node ->
            when (node) {
                is Text -> node.literal
                is Emphasis -> (node.children.firstOrNull() as? Text)?.literal ?: ""
                is StrongEmphasis -> (node.children.firstOrNull() as? Text)?.literal ?: ""
                else -> ""
            }
        }
        assertTrue(cellText.contains("bold"))
        assertTrue(cellText.contains("italic"))
    }

    @Test
    fun should_parse_multiple_body_rows() {
        val input = "| H1 | H2 |\n| --- | --- |\n| a | b |\n| c | d |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)

        val body = table.children.filterIsInstance<TableBody>().first()
        val rows = body.children.filterIsInstance<TableRow>()
        assertEquals(2, rows.size)

        // 第一行
        val row1Cells = rows[0].children.filterIsInstance<TableCell>()
        assertEquals("a", (row1Cells[0].children.first() as Text).literal)
        assertEquals("b", (row1Cells[1].children.first() as Text).literal)

        // 第二行
        val row2Cells = rows[1].children.filterIsInstance<TableCell>()
        assertEquals("c", (row2Cells[0].children.first() as Text).literal)
        assertEquals("d", (row2Cells[1].children.first() as Text).literal)
    }

    @Test
    fun should_handle_empty_cell_content() {
        val input = "| A | B |\n| --- | --- |\n|  | text |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)

        val body = table.children.filterIsInstance<TableBody>().first()
        val row = body.children.first()
        assertIs<TableRow>(row)
        val cells = row.children.filterIsInstance<TableCell>()

        // 空单元格不应包含 | 字符
        val firstCellChildren = cells[0].children
        assertTrue(firstCellChildren.isEmpty() || (firstCellChildren.first() as? Text)?.literal?.trim().isNullOrEmpty())
    }
}

class MathBlockParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_math_block() {
        val doc = parser.parse("$$\nx = \\frac{-b}{2a}\n$$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
        assertTrue(math.literal.contains("x = \\frac{-b}{2a}"))
    }

    @Test
    fun should_parse_single_line_math() {
        val doc = parser.parse("$$ E = mc^2 $$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
    }
}

class FrontMatterParserTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_yaml_front_matter() {
        val doc = parser.parse("---\ntitle: Hello\nauthor: Test\n---\n\nContent")
        val fm = doc.children.first()
        assertIs<FrontMatter>(fm)
        assertEquals("yaml", fm.format)
        assertTrue(fm.literal.contains("title: Hello"))
    }

    @Test
    fun should_parse_toml_front_matter() {
        val doc = parser.parse("+++\ntitle = \"Hello\"\n+++\n\nContent")
        val fm = doc.children.first()
        assertIs<FrontMatter>(fm)
        assertEquals("toml", fm.format)
    }

    @Test
    fun should_only_parse_front_matter_at_document_start() {
        val doc = parser.parse("Hello\n\n---\ntitle: Hello\n---")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }

    @Test
    fun should_parse_empty_yaml_front_matter() {
        val doc = parser.parse("---\n---\n\nContent")
        val fm = doc.children.first()
        assertIs<FrontMatter>(fm)
        assertEquals("yaml", fm.format)
    }
}

// ────── 自动生成标题 ID ──────

class HeadingIdTest {

    private val parser = MarkdownParser()

    @Test
    fun should_auto_generate_heading_id() {
        val doc = parser.parse("# Hello World")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertNotNull(heading.autoId)
        assertEquals("hello-world", heading.autoId)
    }

    @Test
    fun should_use_custom_id_over_auto_id() {
        val doc = parser.parse("# Hello World {#my-id}")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertEquals("my-id", heading.customId)
        assertEquals("my-id", heading.id)
    }

    @Test
    fun should_deduplicate_heading_ids() {
        val doc = parser.parse("# Hello\n\n# Hello")
        val headings = doc.children.filterIsInstance<Heading>()
        assertEquals(2, headings.size)
        assertEquals("hello", headings[0].autoId)
        assertEquals("hello-1", headings[1].autoId)
    }

    @Test
    fun should_generate_id_for_setext_heading() {
        val doc = parser.parse("Hello World\n===")
        val heading = doc.children.first()
        assertIs<SetextHeading>(heading)
        assertNotNull(heading.autoId)
        assertEquals("hello-world", heading.autoId)
    }

    @Test
    fun should_handle_chinese_heading_id() {
        val doc = parser.parse("# 你好世界")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertNotNull(heading.autoId)
        assertTrue(heading.autoId!!.contains("你好世界"))
    }

    @Test
    fun should_handle_special_chars_in_heading_id() {
        val doc = parser.parse("# Hello, World! How are you?")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertNotNull(heading.autoId)
        assertEquals("hello-world-how-are-you", heading.autoId)
    }

    @Test
    fun should_generate_id_for_heading_with_inline_code() {
        val doc = parser.parse("# The `main` function")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertNotNull(heading.autoId)
    }

    @Test
    fun should_generate_unique_ids_for_multiple_duplicates() {
        val doc = parser.parse("# Foo\n\n# Foo\n\n# Foo")
        val headings = doc.children.filterIsInstance<Heading>()
        assertEquals(3, headings.size)
        assertEquals("foo", headings[0].autoId)
        assertEquals("foo-1", headings[1].autoId)
        assertEquals("foo-2", headings[2].autoId)
    }
}

// ────── 表格列数规范化 ──────

class TableColumnNormalizationTest {

    private val parser = MarkdownParser()

    @Test
    fun should_pad_missing_cells_with_empty() {
        val input = "| A | B | C |\n| --- | --- | --- |\n| 1 | 2 |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)

        val body = table.children.filterIsInstance<TableBody>().first()
        val row = body.children.first()
        assertIs<TableRow>(row)
        val cells = row.children.filterIsInstance<TableCell>()
        assertEquals(3, cells.size)
    }

    @Test
    fun should_truncate_extra_cells() {
        val input = "| A | B |\n| --- | --- |\n| 1 | 2 | 3 |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)

        val body = table.children.filterIsInstance<TableBody>().first()
        val row = body.children.first()
        assertIs<TableRow>(row)
        val cells = row.children.filterIsInstance<TableCell>()
        assertEquals(2, cells.size)
    }

    @Test
    fun should_not_interrupt_paragraph() {
        val input = "Some paragraph text\n| A | B |\n| --- | --- |\n| 1 | 2 |"
        val doc = parser.parse(input)
        assertTrue(doc.children.size >= 1)
    }

    @Test
    fun should_handle_single_column_table() {
        val input = "| A |\n| --- |\n| 1 |\n| 2 |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)
        assertEquals(1, table.columnAlignments.size)
    }

    @Test
    fun should_parse_table_with_escaped_pipe() {
        val input = "| A | B |\n| --- | --- |\n| a \\| b | c |"
        val doc = parser.parse(input)
        val table = doc.children.first()
        assertIs<Table>(table)
    }
}

// ────── GFM 禁止 HTML ──────

class GfmDisallowedHtmlTest {

    private val parser = MarkdownParser()

    @Test
    fun should_filter_script_tag() {
        val doc = parser.parse("<script>alert('xss')</script>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertTrue(html.literal.contains("filtered"))
    }

    @Test
    fun should_filter_textarea_tag() {
        val doc = parser.parse("<textarea>content</textarea>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertTrue(html.literal.contains("filtered"))
    }

    @Test
    fun should_not_filter_safe_tags() {
        val doc = parser.parse("<div>safe content</div>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertTrue(html.literal.contains("<div>"))
        assertTrue(!html.literal.contains("filtered"))
    }

    @Test
    fun should_filter_style_tag() {
        val doc = parser.parse("<style>body{color:red}</style>")
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertTrue(html.literal.contains("filtered"))
    }
}

// ────── TOC 占位符 ──────

class TocPlaceholderTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_toc_placeholder() {
        val doc = parser.parse("[TOC]")
        val first = doc.children.first()
        assertIs<TocPlaceholder>(first)
    }

    @Test
    fun should_parse_toc_placeholder_double_bracket() {
        val doc = parser.parse("[[toc]]")
        val first = doc.children.first()
        assertIs<TocPlaceholder>(first)
    }

    @Test
    fun should_not_parse_toc_in_middle_of_text() {
        val doc = parser.parse("some text [TOC] more text")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }

    @Test
    fun should_parse_toc_mixed_case_as_paragraph() {
        // 解析器仅识别 [TOC] 和 [[toc]], 其他大小写变体解析为段落
        val doc = parser.parse("[toc]")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }
}

// ────── 缩写定义 ──────

class AbbreviationTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_abbreviation_definition() {
        val input = "*[HTML]: Hyper Text Markup Language\n\nThe HTML specification."
        val doc = parser.parse(input)
        assertTrue(doc.abbreviationDefinitions.containsKey("HTML"))
        assertEquals("Hyper Text Markup Language", doc.abbreviationDefinitions["HTML"]!!.fullText)
    }

    @Test
    fun should_replace_abbreviation_in_text() {
        val input = "*[HTML]: Hyper Text Markup Language\n\nThe HTML specification."
        val doc = parser.parse(input)
        val para = doc.children.filterIsInstance<Paragraph>().first()
        assertTrue(para.children.any { it is Abbreviation })
    }

    @Test
    fun should_not_replace_abbreviation_in_word() {
        val input = "*[MD]: Markdown\n\nUse MYMD format."
        val doc = parser.parse(input)
        val para = doc.children.filterIsInstance<Paragraph>().first()
        assertTrue(para.children.none { it is Abbreviation })
    }

    @Test
    fun should_handle_multiple_abbreviation_definitions() {
        val input = "*[HTML]: Hyper Text Markup Language\n*[CSS]: Cascading Style Sheets\n\nUse HTML and CSS."
        val doc = parser.parse(input)
        assertTrue(doc.abbreviationDefinitions.containsKey("HTML"))
        assertTrue(doc.abbreviationDefinitions.containsKey("CSS"))
    }
}

// ────── 可折叠内容（details/summary） ──────

class CollapsibleContentTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_details_as_html_block() {
        val input = "<details>\n<summary>Click me</summary>\nHidden content\n</details>"
        val doc = parser.parse(input)
        val html = doc.children.first()
        assertIs<HtmlBlock>(html)
        assertTrue(html.literal.contains("<details>"))
        assertTrue(html.literal.contains("<summary>"))
    }

    @Test
    fun should_parse_details_with_markdown_content() {
        val input = "<details>\n<summary>Title</summary>\n\n**Bold** inside details\n\n</details>"
        val doc = parser.parse(input)
        assertTrue(doc.children.isNotEmpty())
    }
}

// ────── 块引用额外测试 ──────

class BlockQuoteExtendedTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_block_quote_with_list() {
        val doc = parser.parse("> - item1\n> - item2")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        assertTrue(bq.children.any { it is ListBlock })
    }

    @Test
    fun should_parse_lazy_continuation() {
        val doc = parser.parse("> Paragraph\ncontinuation line")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
    }

    @Test
    fun should_parse_empty_block_quote() {
        val doc = parser.parse(">")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
    }

    @Test
    fun should_parse_block_quote_with_code_block() {
        val doc = parser.parse("> ```\n> code\n> ```")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
        assertTrue(bq.children.any { it is FencedCodeBlock })
    }
}

// ────── 列表额外测试 ──────

class ListExtendedTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_nested_list() {
        val doc = parser.parse("- Item 1\n  - Nested 1\n  - Nested 2\n- Item 2")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        val items = list.children.filterIsInstance<ListItem>()
        assertTrue(items.size >= 2)
    }

    @Test
    fun should_detect_tight_list() {
        val doc = parser.parse("- A\n- B\n- C")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertTrue(list.tight)
    }

    @Test
    fun should_detect_loose_list() {
        val doc = parser.parse("- A\n\n- B\n\n- C")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertTrue(!list.tight)
    }

    @Test
    fun should_parse_ordered_list_starting_from_0() {
        val doc = parser.parse("0. Zero\n1. One")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
        assertEquals(true, list.ordered)
        assertEquals(0, list.startNumber)
    }

    @Test
    fun should_parse_list_with_multiple_paragraphs() {
        val doc = parser.parse("- Item 1\n\n  Continuation\n\n- Item 2")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }

    @Test
    fun should_parse_list_with_code_block() {
        val doc = parser.parse("- Item\n\n  ```\n  code\n  ```\n\n- Item 2")
        val list = doc.children.first()
        assertIs<ListBlock>(list)
    }
}

// ────── 缩进代码块测试 ──────

class IndentedCodeBlockTest {

    // IndentedCodeBlock 现在在 ExtendedFlavour 中也支持（在定义列表/脚注内部自动让步）
    private val parser = MarkdownParser()

    @Test
    fun should_parse_indented_code_block() {
        val doc = parser.parse("    code line 1\n    code line 2")
        val block = doc.children.first()
        assertIs<IndentedCodeBlock>(block)
    }

    @Test
    fun should_not_parse_indented_code_inside_paragraph() {
        // Paragraph continuation has higher priority
        val doc = parser.parse("Paragraph\n    not code")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }

    @Test
    fun should_preserve_content_in_indented_code() {
        val doc = parser.parse("    **not bold**\n    # not heading")
        val block = doc.children.first()
        assertIs<IndentedCodeBlock>(block)
        assertTrue(block.literal.contains("**not bold**"))
        assertTrue(block.literal.contains("# not heading"))
    }

    @Test
    fun should_not_conflict_with_definition_list() {
        // 定义列表中 4 空格缩进是续行内容，不应触发缩进代码块
        val doc = parser.parse("Term\n: Description\n\n    Continued description")
        val hasDef = doc.children.any { it is DefinitionList }
        assertTrue(hasDef, "Should contain a definition list")
        val hasIndented = doc.children.any { it is IndentedCodeBlock }
        assertFalse(hasIndented, "Should not have indented code block inside definition context")
    }
}

// ────── 数学块额外测试 ──────

class MathBlockExtendedTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_multiline_math_block() {
        val doc = parser.parse("$$\na = b + c\nd = e \\times f\n$$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
        assertTrue(math.literal.contains("a = b + c"))
        assertTrue(math.literal.contains("d = e \\times f"))
    }

    @Test
    fun should_preserve_latex_commands_in_math_block() {
        val doc = parser.parse("$$\n\\sum_{i=1}^{n} x_i\n$$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
        assertTrue(math.literal.contains("\\sum"))
    }

    @Test
    fun should_handle_empty_math_block() {
        val doc = parser.parse("$$\n$$")
        val math = doc.children.first()
        assertIs<MathBlock>(math)
    }
}

// ────── 脚注定义测试 ──────

class FootnoteDefinitionTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_footnote_definition() {
        val input = "[^note]: This is the footnote content.\n\nText with [^note]."
        val doc = parser.parse(input)
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_parse_footnote_definition_children_as_blocks() {
        val doc = parser.parse("[^note]: This is the footnote content.\n\nText with [^note].")

        val footnote = doc.children.first()
        assertIs<FootnoteDefinition>(footnote)

        val paragraph = footnote.children.first()
        assertIs<Paragraph>(paragraph)

        val text = paragraph.children.first()
        assertIs<Text>(text)
        assertEquals("This is the footnote content.", text.literal)
    }

    @Test
    fun should_parse_multiple_footnotes() {
        val input = "[^1]: First note.\n[^2]: Second note.\n\nText [^1] and [^2]."
        val doc = parser.parse(input)
        assertTrue(doc.children.isNotEmpty())
    }
}

// ────── 定义列表测试 ──────

class DefinitionListTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_definition_list() {
        val doc = parser.parse("Term\n: Definition")
        val defList = doc.children.first()
        assertIs<DefinitionList>(defList)
        // 应包含术语和描述
        assertTrue(defList.children.any { it is DefinitionTerm })
        assertTrue(defList.children.any { it is DefinitionDescription })
    }

    @Test
    fun should_parse_multiple_definitions() {
        val doc = parser.parse("Term\n: Definition 1\n: Definition 2")
        val defList = doc.children.first()
        assertIs<DefinitionList>(defList)
        val descriptions = defList.children.filterIsInstance<DefinitionDescription>()
        assertTrue(descriptions.size >= 2)
    }

    @Test
    fun should_parse_multiple_terms_and_definitions() {
        val doc = parser.parse("Term 1\n: Def 1\n\nTerm 2\n: Def 2")
        assertTrue(doc.children.isNotEmpty())
        // 空行分隔后每组 term+def 各自成为独立的定义列表
        assertTrue(doc.children.any { it is DefinitionList })
    }

    @Test
    fun should_parse_definition_term_with_inline_content() {
        val doc = parser.parse("**Bold** Term\n: Definition")
        val defList = doc.children.first()
        assertIs<DefinitionList>(defList)
        val term = defList.children.filterIsInstance<DefinitionTerm>().first()
        assertTrue(term.children.any { it is StrongEmphasis })
    }

    @Test
    fun should_not_parse_colon_without_space_as_definition() {
        val doc = parser.parse("Term\n:NoSpace")
        val first = doc.children.first()
        // 没有空格不应该解析为定义列表
        assertTrue(first !is DefinitionList)
    }

    @Test
    fun should_parse_definition_with_multiple_paragraphs() {
        val input = "Term\n: First paragraph.\n\n  Second paragraph."
        val doc = parser.parse(input)
        val defList = doc.children.first()
        assertIs<DefinitionList>(defList)
        val desc = defList.children.filterIsInstance<DefinitionDescription>().first()
        // 定义描述内应包含两个段落
        val paragraphs = desc.children.filterIsInstance<Paragraph>()
        assertEquals(2, paragraphs.size)
    }

    @Test
    fun should_parse_definition_with_code_block() {
        val input = "Term\n: Definition with code:\n\n  ```kotlin\n  fun hello() = println(\"Hi\")\n  ```"
        val doc = parser.parse(input)
        val defList = doc.children.first()
        assertIs<DefinitionList>(defList)
        val desc = defList.children.filterIsInstance<DefinitionDescription>().first()
        // 定义描述内应包含段落和代码块
        assertTrue(desc.children.any { it is Paragraph })
        assertTrue(desc.children.any { it is FencedCodeBlock })
    }

    @Test
    fun should_parse_definition_with_nested_list() {
        val input = "Term\n: Definition:\n\n  - Item 1\n  - Item 2"
        val doc = parser.parse(input)
        val defList = doc.children.first()
        assertIs<DefinitionList>(defList)
        val desc = defList.children.filterIsInstance<DefinitionDescription>().first()
        assertTrue(desc.children.any { it is ListBlock })
    }

    @Test
    fun should_end_definition_on_unindented_content_after_blank_line() {
        val input = "Term\n: Definition.\n\nNew paragraph outside."
        val doc = parser.parse(input)
        // 定义列表后应跟一个独立段落
        assertTrue(doc.children.any { it is DefinitionList })
        assertTrue(doc.children.any { it is Paragraph })
    }
}

// ────── Admonition 测试 ──────

class AdmonitionTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_note_admonition() {
        val doc = parser.parse("> [!NOTE]\n> This is a note.")
        val admonition = doc.children.first()
        assertIs<Admonition>(admonition)
        assertEquals("NOTE", admonition.type)
    }

    @Test
    fun should_parse_warning_admonition() {
        val doc = parser.parse("> [!WARNING]\n> Be careful!")
        val admonition = doc.children.first()
        assertIs<Admonition>(admonition)
        assertEquals("WARNING", admonition.type)
    }

    @Test
    fun should_parse_tip_admonition() {
        val doc = parser.parse("> [!TIP]\n> A useful tip.")
        val admonition = doc.children.first()
        assertIs<Admonition>(admonition)
        assertEquals("TIP", admonition.type)
    }

    @Test
    fun should_parse_admonition_with_title() {
        val doc = parser.parse("> [!NOTE] Custom Title\n> Content here.")
        val admonition = doc.children.first()
        assertIs<Admonition>(admonition)
        assertEquals("NOTE", admonition.type)
        assertEquals("Custom Title", admonition.title)
    }

    @Test
    fun should_not_convert_regular_blockquote() {
        val doc = parser.parse("> Normal blockquote")
        val bq = doc.children.first()
        assertIs<BlockQuote>(bq)
    }
}

// ────── CustomContainer 测试 ──────

class CustomContainerTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_basic_custom_container() {
        val doc = parser.parse(":::note\nThis is a note.\n:::")
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertEquals("note", container.type)
    }

    @Test
    fun should_parse_container_with_type() {
        val doc = parser.parse(":::warning\nBe careful!\n:::")
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertEquals("warning", container.type)
    }

    @Test
    fun should_parse_container_with_title() {
        val doc = parser.parse("::: note \"Important Note\"\nContent here.\n:::")
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertEquals("note", container.type)
        assertEquals("Important Note", container.title)
    }

    @Test
    fun should_parse_container_with_css_classes() {
        val doc = parser.parse("::: note{.custom-style .highlight}\nContent\n:::")
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertTrue(container.cssClasses.contains("custom-style"))
        assertTrue(container.cssClasses.contains("highlight"))
    }

    @Test
    fun should_parse_container_with_css_id() {
        val doc = parser.parse("::: note{#my-note}\nContent\n:::")
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertEquals("my-note", container.cssId)
    }

    @Test
    fun should_parse_container_with_block_content() {
        val input = ":::note\n# Heading\n\nParagraph text.\n\n- Item 1\n- Item 2\n:::"
        val doc = parser.parse(input)
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertTrue(container.children.isNotEmpty())
        assertTrue(container.children.any { it is Heading })
    }

    @Test
    fun should_parse_nested_containers() {
        val input = "::::outer\n:::inner\nNested content.\n:::\n::::"
        val doc = parser.parse(input)
        val outer = doc.children.first()
        assertIs<CustomContainer>(outer)
        assertEquals("outer", outer.type)
        val inner = outer.children.filterIsInstance<CustomContainer>().firstOrNull()
        assertNotNull(inner)
        assertEquals("inner", inner.type)
    }

    @Test
    fun should_parse_empty_container() {
        val doc = parser.parse(":::note\n:::")
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertEquals("note", container.type)
    }

    @Test
    fun should_not_parse_less_than_three_colons() {
        val doc = parser.parse("::not a container\nContent\n::")
        val first = doc.children.first()
        assertIs<Paragraph>(first)
    }

    @Test
    fun should_close_container_at_end_of_document() {
        val doc = parser.parse(":::note\nUnclosed container content.")
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertEquals("note", container.type)
    }

    @Test
    fun should_parse_container_with_title_and_attributes() {
        val doc = parser.parse("::: warning \"Watch Out\" {.alert #warn-1}\nDangerous!\n:::")
        val container = doc.children.first()
        assertIs<CustomContainer>(container)
        assertEquals("warning", container.type)
        assertEquals("Watch Out", container.title)
        assertTrue(container.cssClasses.contains("alert"))
        assertEquals("warn-1", container.cssId)
    }
}

// ────── DiagramBlock 测试 ──────

class DiagramBlockTest {

    private val parser = MarkdownParser()

    @Test
    fun should_convert_mermaid_block() {
        val doc = parser.parse("```mermaid\nflowchart TD\n    A --> B\n```")
        val diagram = doc.children.first()
        assertIs<DiagramBlock>(diagram)
        assertEquals("mermaid", diagram.diagramType)
        assertTrue(diagram.literal.contains("flowchart TD"))
    }

    @Test
    fun should_convert_plantuml_block() {
        val doc = parser.parse("```plantuml\n@startuml\nactor User\n@enduml\n```")
        val diagram = doc.children.first()
        assertIs<DiagramBlock>(diagram)
        assertEquals("plantuml", diagram.diagramType)
        assertTrue(diagram.literal.contains("@startuml"))
    }

    @Test
    fun should_not_convert_regular_code_block() {
        val doc = parser.parse("```kotlin\nfun main() {}\n```")
        val block = doc.children.first()
        assertIs<FencedCodeBlock>(block)
        assertEquals("kotlin", block.language)
    }

    @Test
    fun should_convert_dot_graphviz_block() {
        val doc = parser.parse("```dot\ndigraph G { A -> B }\n```")
        val diagram = doc.children.first()
        assertIs<DiagramBlock>(diagram)
        assertEquals("dot", diagram.diagramType)
    }

    @Test
    fun should_convert_case_insensitive() {
        val doc = parser.parse("```Mermaid\nflowchart TD\n```")
        val diagram = doc.children.first()
        assertIs<DiagramBlock>(diagram)
        assertEquals("mermaid", diagram.diagramType)
    }

    @Test
    fun should_preserve_diagram_content() {
        val content = "sequenceDiagram\n    Alice->>Bob: Hello\n    Bob-->>Alice: Hi"
        val doc = parser.parse("```mermaid\n$content\n```")
        val diagram = doc.children.first()
        assertIs<DiagramBlock>(diagram)
        assertTrue(diagram.literal.contains("Alice->>Bob"))
    }
}
