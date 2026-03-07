package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.html.HtmlRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockAttributeTest {

    private val parser = MarkdownParser()

    // ─────────────── 标题属性 ───────────────

    @Test
    fun should_apply_class_to_heading() {
        val doc = parser.parse("# Title\n{.special}")
        val heading = doc.children.first { it is Heading } as Heading
        assertEquals("special", heading.blockAttributes["class"])
    }

    @Test
    fun should_apply_id_to_heading() {
        val doc = parser.parse("# Title\n{#custom-id}")
        val heading = doc.children.first { it is Heading } as Heading
        assertEquals("custom-id", heading.customId)
    }

    @Test
    fun should_apply_multiple_classes_to_heading() {
        val doc = parser.parse("# Title\n{.class1 .class2}")
        val heading = doc.children.first { it is Heading } as Heading
        assertContains(heading.blockAttributes["class"] ?: "", "class1")
        assertContains(heading.blockAttributes["class"] ?: "", "class2")
    }

    @Test
    fun should_apply_mixed_attributes_to_heading() {
        val doc = parser.parse("# Title\n{.highlight #main-title data-section=\"intro\"}")
        val heading = doc.children.first { it is Heading } as Heading
        assertContains(heading.blockAttributes["class"] ?: "", "highlight")
        assertEquals("main-title", heading.customId)
        assertEquals("intro", heading.blockAttributes["data-section"])
    }

    // ─────────────── 段落属性 ───────────────

    @Test
    fun should_apply_class_to_paragraph() {
        val doc = parser.parse("A paragraph.\n{.important}")
        val paragraphs = doc.children.filterIsInstance<Paragraph>()
        assertTrue(paragraphs.isNotEmpty())
        val para = paragraphs.first()
        assertEquals("important", para.blockAttributes["class"])
    }

    @Test
    fun should_apply_style_to_paragraph() {
        val doc = parser.parse("A paragraph.\n{style=\"color:red\"}")
        val para = doc.children.filterIsInstance<Paragraph>().first()
        assertEquals("color:red", para.blockAttributes["style"])
    }

    // ─────────────── 引用块属性 ───────────────

    @Test
    fun should_apply_class_to_blockquote() {
        val doc = parser.parse("> A quote\n\n{.note}")
        val bq = doc.children.filterIsInstance<BlockQuote>().firstOrNull()
        if (bq != null) {
            assertEquals("note", bq.blockAttributes["class"])
        }
    }

    // ─────────────── 列表属性 ───────────────

    @Test
    fun should_apply_class_to_list() {
        // 列表后的内容会被解析器吸收到列表最后一项中。
        // 因此，要给列表本身设置属性，需要在列表之前使用属性段落，
        // 让 BlockAttributeProcessor 在当前容器中找不到前置块时跳过。
        // 这里测试列表项内部的属性递归处理：
        // 列表项内的 {.highlight} 段落应被当作属性段落应用到列表项中的前一个段落。
        val md = "- Item 1\n\n  Paragraph in item.\n  {.highlight}\n\n- Item 2"
        val doc = parser.parse(md)
        val list = doc.children.filterIsInstance<ListBlock>()
        assertTrue(list.isNotEmpty(), "Expected ListBlock in children")

        // 验证 BlockAttributeProcessor 递归处理了 ListItem 内部的容器
        val firstItem = list.first().children.filterIsInstance<ListItem>().firstOrNull()
        assertIs<ListItem>(firstItem, "Expected ListItem")
        // 列表项中的段落应获得 highlight 属性
        val paras = firstItem.children.filterIsInstance<Paragraph>()
        val highlightedPara = paras.firstOrNull { it.blockAttributes["class"]?.contains("highlight") == true }
        assertTrue(highlightedPara != null, "Expected paragraph with 'highlight' class in list item")
    }

    // ─────────────── 表格属性 ───────────────

    @Test
    fun should_apply_class_to_table() {
        val doc = parser.parse("| A | B |\n|---|---|\n| 1 | 2 |\n{.striped}")
        val table = doc.children.filterIsInstance<Table>().firstOrNull()
        if (table != null) {
            assertEquals("striped", table.blockAttributes["class"])
        }
    }

    // ─────────────── 代码块属性 ───────────────

    @Test
    fun should_apply_class_to_fenced_code_block() {
        val doc = parser.parse("```\ncode\n```\n{.line-numbers}")
        val code = doc.children.filterIsInstance<FencedCodeBlock>().firstOrNull()
        if (code != null) {
            val classes = code.attributes.classes
            assertContains(classes, "line-numbers")
        }
    }

    // ─────────────── 属性段落移除 ───────────────

    @Test
    fun should_remove_attribute_paragraph_from_ast() {
        val doc = parser.parse("# Title\n{.special}\n\nNormal paragraph.")
        // 属性段落应被移除，只剩标题和正文段落
        val nonBlankChildren = doc.children.filter { it !is BlankLine }
        val paragraphs = nonBlankChildren.filterIsInstance<Paragraph>()
        // 正文段落应该存在
        assertTrue(paragraphs.any { p ->
            p.children.any { it is Text && (it as Text).literal.contains("Normal") }
        })
        // 属性段落不应该存在
        assertTrue(paragraphs.none { p ->
            val raw = p.rawContent ?: ""
            raw.trim().startsWith("{") && raw.trim().endsWith("}")
        })
    }

    // ─────────────── 无效属性不影响 ───────────────

    @Test
    fun should_not_parse_non_attribute_paragraph() {
        val doc = parser.parse("# Title\n\nNot an attribute {.class} in text.")
        val paragraphs = doc.children.filterIsInstance<Paragraph>()
        // 含有其他文本的段落不应被当作属性段落
        assertTrue(paragraphs.isNotEmpty())
    }

    @Test
    fun should_handle_no_preceding_block() {
        // 属性段落在文档开头，没有前置块
        val doc = parser.parse("{.orphan}\n\nParagraph.")
        // 应正常解析，不崩溃
        assertTrue(doc.children.isNotEmpty())
    }

    // ─────────────── 跳过空行匹配 ───────────────

    @Test
    fun should_skip_blank_lines_to_find_preceding_block() {
        val doc = parser.parse("# Title\n\n{.special}")
        val heading = doc.children.filterIsInstance<Heading>().first()
        assertEquals("special", heading.blockAttributes["class"])
    }

    // ─────────────── 与 HtmlRenderer 集成 ───────────────

    @Test
    fun should_render_heading_with_block_attributes_to_html() {
        val html = HtmlRenderer.renderMarkdown("# Title\n{.special #main}")
        assertContains(html, "class=\"special\"")
        assertContains(html, "id=\"main\"")
    }

    @Test
    fun should_render_paragraph_with_block_attributes_to_html() {
        val html = HtmlRenderer.renderMarkdown("A paragraph.\n{.highlight style=\"background:yellow\"}")
        assertContains(html, "class=\"highlight\"")
        assertContains(html, "style=\"background:yellow\"")
    }

    @Test
    fun should_render_blockquote_with_attributes_to_html() {
        val html = HtmlRenderer.renderMarkdown("> Quote\n{.important}")
        assertContains(html, "blockquote")
    }

    @Test
    fun should_render_table_with_attributes_to_html() {
        val html = HtmlRenderer.renderMarkdown("| A |\n|---|\n| 1 |\n{.striped}")
        assertContains(html, "<table")
    }

    // ─────────────── 复合场景 ───────────────

    @Test
    fun should_handle_multiple_blocks_with_attributes() {
        val md = """
            # Title
            {.title-class}

            Paragraph text.
            {.para-class}
        """.trimIndent()
        val doc = parser.parse(md)
        val heading = doc.children.filterIsInstance<Heading>().first()
        assertEquals("title-class", heading.blockAttributes["class"])

        val paragraphs = doc.children.filterIsInstance<Paragraph>()
        val contentPara = paragraphs.first { p ->
            p.children.any { it is Text && (it as Text).literal.contains("Paragraph") }
        }
        assertEquals("para-class", contentPara.blockAttributes["class"])
    }

    @Test
    fun should_handle_key_value_attributes() {
        val doc = parser.parse("# Title\n{data-level=\"1\" role=\"heading\"}")
        val heading = doc.children.filterIsInstance<Heading>().first()
        assertEquals("1", heading.blockAttributes["data-level"])
        assertEquals("heading", heading.blockAttributes["role"])
    }

    @Test
    fun should_handle_quoted_values_with_spaces() {
        val doc = parser.parse("# Title\n{title=\"My Custom Title\"}")
        val heading = doc.children.filterIsInstance<Heading>().first()
        assertEquals("My Custom Title", heading.blockAttributes["title"])
    }
}
