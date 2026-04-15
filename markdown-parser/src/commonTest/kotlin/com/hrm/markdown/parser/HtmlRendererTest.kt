package com.hrm.markdown.parser

import com.hrm.markdown.parser.html.HtmlRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class HtmlRendererTest {

    private fun render(markdown: String): String {
        return HtmlRenderer.renderMarkdown(markdown)
    }

    // ─────────────── 基础块级元素 ───────────────

    @Test
    fun should_render_heading_h1() {
        val html = render("# Hello World")
        assertContains(html, "<h1")
        assertContains(html, "Hello World")
        assertContains(html, "</h1>")
    }

    @Test
    fun should_render_heading_h2_to_h6() {
        for (level in 2..6) {
            val prefix = "#".repeat(level)
            val html = render("$prefix Heading $level")
            assertContains(html, "<h$level")
            assertContains(html, "Heading $level")
            assertContains(html, "</h$level>")
        }
    }

    @Test
    fun should_render_heading_with_auto_id() {
        val html = render("# Hello World")
        assertContains(html, "id=\"hello-world\"")
    }

    @Test
    fun should_render_heading_with_custom_id() {
        val html = render("# Hello World {#custom}")
        assertContains(html, "id=\"custom\"")
    }

    @Test
    fun should_render_paragraph() {
        val html = render("This is a paragraph.")
        assertEquals("<p>This is a paragraph.</p>\n", html)
    }

    @Test
    fun should_render_thematic_break() {
        val html = render("---")
        assertContains(html, "<hr />")
    }

    @Test
    fun should_render_fenced_code_block() {
        val md = "```kotlin\nfun main() {}\n```"
        val html = render(md)
        assertContains(html, "<pre>")
        assertContains(html, "<code class=\"language-kotlin\">")
        assertContains(html, "fun main() {}")
        assertContains(html, "</code>")
        assertContains(html, "</pre>")
    }

    @Test
    fun should_render_fenced_code_block_without_language() {
        val md = "```\nhello\n```"
        val html = render(md)
        assertContains(html, "<pre><code>")
        assertContains(html, "hello")
    }

    @Test
    fun should_render_indented_code_block() {
        val md = "    code line 1\n    code line 2"
        val html = render(md)
        assertContains(html, "<pre><code>")
        assertContains(html, "code line 1")
    }

    @Test
    fun should_render_blockquote() {
        val md = "> This is a quote."
        val html = render(md)
        assertContains(html, "<blockquote>")
        assertContains(html, "This is a quote.")
        assertContains(html, "</blockquote>")
    }

    @Test
    fun should_render_unordered_list() {
        val md = "- Item 1\n- Item 2\n- Item 3"
        val html = render(md)
        assertContains(html, "<ul>")
        assertContains(html, "<li>")
        assertContains(html, "Item 1")
        assertContains(html, "Item 2")
        assertContains(html, "Item 3")
        assertContains(html, "</ul>")
    }

    @Test
    fun should_render_ordered_list() {
        val md = "1. First\n2. Second\n3. Third"
        val html = render(md)
        assertContains(html, "<ol>")
        assertContains(html, "<li>")
        assertContains(html, "First")
        assertContains(html, "</ol>")
    }

    @Test
    fun should_render_ordered_list_with_start_number() {
        val md = "3. First\n4. Second"
        val html = render(md)
        assertContains(html, "start=\"3\"")
    }

    @Test
    fun should_render_task_list() {
        val md = "- [x] Done\n- [ ] Todo"
        val html = render(md)
        assertContains(html, "type=\"checkbox\"")
        assertContains(html, "checked")
        assertContains(html, "Done")
        assertContains(html, "Todo")
    }

    @Test
    fun should_render_table() {
        val md = "| A | B |\n|---|---|\n| 1 | 2 |"
        val html = render(md)
        assertContains(html, "<table>")
        assertContains(html, "<thead>")
        assertContains(html, "<tbody>")
        assertContains(html, "<th>")
        assertContains(html, "<td>")
        assertContains(html, "</table>")
    }

    @Test
    fun should_render_table_alignment() {
        val md = "| Left | Center | Right |\n|:-----|:------:|------:|\n| 1 | 2 | 3 |"
        val html = render(md)
        assertContains(html, "align=\"left\"")
        assertContains(html, "align=\"center\"")
        assertContains(html, "align=\"right\"")
    }

    @Test
    fun should_render_html_block_passthrough() {
        val md = "<div>raw html</div>"
        val html = render(md)
        assertContains(html, "<div>raw html</div>")
    }

    @Test
    fun should_escape_html_block_when_configured() {
        val md = "<div>raw html</div>"
        val parser = MarkdownParser()
        val doc = parser.parse(md)
        val html = HtmlRenderer(escapeHtml = true).render(doc)
        assertContains(html, "&lt;div&gt;")
    }

    // ─────────────── 行内元素 ───────────────

    @Test
    fun should_render_emphasis() {
        val html = render("*italic* text")
        assertContains(html, "<em>italic</em>")
    }

    @Test
    fun should_render_strong_emphasis() {
        val html = render("**bold** text")
        assertContains(html, "<strong>bold</strong>")
    }

    @Test
    fun should_render_strikethrough() {
        val html = render("~~deleted~~ text")
        assertContains(html, "<del>deleted</del>")
    }

    @Test
    fun should_render_inline_code() {
        val html = render("`code` text")
        assertContains(html, "<code>code</code>")
    }

    @Test
    fun should_render_link() {
        val html = render("[Click](https://example.com \"Title\")")
        assertContains(html, "<a href=\"https://example.com\" title=\"Title\">")
        assertContains(html, "Click")
        assertContains(html, "</a>")
    }

    @Test
    fun should_render_link_with_attributes() {
        val html = render("[Click](https://example.com){target=\"_blank\" rel=\"nofollow\"}")
        assertContains(html, "target=\"_blank\"")
        assertContains(html, "rel=\"nofollow\"")
    }

    @Test
    fun should_render_image() {
        val html = render("![Alt](image.png \"Title\")")
        assertContains(html, "<img")
        assertContains(html, "src=\"image.png\"")
        assertContains(html, "alt=\"Alt\"")
        assertContains(html, "title=\"Title\"")
    }

    @Test
    fun should_render_autolink() {
        val html = render("<https://example.com>")
        assertContains(html, "<a href=\"https://example.com\">")
        assertContains(html, "https://example.com")
    }

    @Test
    fun should_render_hard_line_break() {
        val html = render("Line 1  \nLine 2")
        assertContains(html, "<br />")
    }

    @Test
    fun should_render_escaped_char() {
        val html = render("\\*not italic\\*")
        assertContains(html, "*not italic*")
    }

    @Test
    fun should_render_html_entity() {
        val html = render("&amp; &lt;")
        assertContains(html, "&amp;")
        assertContains(html, "&lt;")
    }

    // ─────────────── 扩展行内 ───────────────

    @Test
    fun should_render_highlight() {
        val html = render("==highlight==")
        assertContains(html, "<mark>highlight</mark>")
    }

    @Test
    fun should_render_superscript() {
        val html = render("x^2^")
        assertContains(html, "<sup>2</sup>")
    }

    @Test
    fun should_render_subscript() {
        val html = render("H~2~O")
        assertContains(html, "<sub>2</sub>")
    }

    @Test
    fun should_render_inserted_text() {
        val html = render("++inserted++")
        assertContains(html, "<ins>inserted</ins>")
    }

    @Test
    fun should_render_inline_math() {
        val html = render("\$E=mc^2\$")
        assertContains(html, "class=\"math-inline\"")
        assertContains(html, "\\(E=mc^2\\)")
    }

    @Test
    fun should_render_keyboard_input() {
        val html = render("<kbd>Ctrl</kbd>")
        assertContains(html, "<kbd>Ctrl</kbd>")
    }

    @Test
    fun should_render_styled_text() {
        val html = render("[red text]{.red #my-id style=\"color:red\"}")
        assertContains(html, "<span")
        assertContains(html, "class=\"red\"")
        assertContains(html, "id=\"my-id\"")
        assertContains(html, "red text")
    }

    @Test
    fun should_render_abbreviation() {
        val md = "*[HTML]: HyperText Markup Language\n\nThe HTML spec."
        val html = render(md)
        assertContains(html, "<abbr")
        assertContains(html, "title=\"HyperText Markup Language\"")
        assertContains(html, "HTML")
    }

    // ─────────────── 扩展块级 ───────────────

    @Test
    fun should_render_math_block() {
        val md = "$$\ny = mx + b\n$$"
        val html = render(md)
        assertContains(html, "class=\"math-display\"")
        assertContains(html, "\\[y = mx + b\\]")
    }

    @Test
    fun should_render_admonition() {
        val md = "> [!NOTE]\n> This is a note."
        val html = render(md)
        assertContains(html, "admonition")
    }

    @Test
    fun should_render_definition_list() {
        val md = "Term\n: Definition here"
        val html = render(md)
        assertContains(html, "<dl>")
        assertContains(html, "<dt>")
        assertContains(html, "<dd>")
        assertContains(html, "</dl>")
    }

    @Test
    fun should_render_footnote() {
        val md = "Text[^1]\n\n[^1]: Footnote content"
        val html = render(md)
        assertContains(html, "footnote-ref")
        assertContains(html, "fn-1")
        assertContains(html, "Footnote content")
    }

    @Test
    fun should_render_custom_container() {
        val md = "::: warning \"Warning!\"\nContent here.\n:::"
        val html = render(md)
        assertContains(html, "custom-container")
        assertContains(html, "warning")
    }

    @Test
    fun should_render_page_break() {
        val md = "***pagebreak***"
        val html = render(md)
        assertContains(html, "page-break")
    }

    // ─────────────── HTML 转义 ───────────────

    @Test
    fun should_escape_special_chars_in_text() {
        val html = render("Use 5 > 3 & 2 < 4")
        assertContains(html, "&gt;")
        assertContains(html, "&amp;")
        assertContains(html, "&lt;")
    }

    @Test
    fun should_escape_special_chars_in_code_block() {
        val md = "```\n<div>&amp;</div>\n```"
        val html = render(md)
        assertContains(html, "&lt;div&gt;")
        assertContains(html, "&amp;amp;")
    }

    // ─────────────── 配置选项 ───────────────

    @Test
    fun should_use_custom_soft_break() {
        val parser = MarkdownParser()
        val doc = parser.parse("Line 1\nLine 2")
        val html = HtmlRenderer(softBreak = "<br />\n").render(doc)
        assertContains(html, "<br />\n")
    }

    @Test
    fun should_not_use_xhtml_self_closing_when_disabled() {
        val parser = MarkdownParser()
        val doc = parser.parse("---")
        val html = HtmlRenderer(xhtml = false).render(doc)
        assertContains(html, "<hr>")
    }

    // ─────────────── 复杂文档 ───────────────

    @Test
    fun should_render_complex_document() {
        val md = """
            # Title

            A paragraph with **bold** and *italic*.

            - Item 1
            - Item 2

            > Blockquote

            ```kotlin
            val x = 1
            ```

            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()

        val html = render(md)
        assertContains(html, "<h1")
        assertContains(html, "<strong>bold</strong>")
        assertContains(html, "<em>italic</em>")
        assertContains(html, "<ul>")
        assertContains(html, "<blockquote>")
        assertContains(html, "<pre>")
        assertContains(html, "<table>")
    }

    @Test
    fun should_render_tight_list_without_p_tags() {
        val md = "- Item 1\n- Item 2"
        val html = render(md)
        // 紧凑列表不应包含 <p> 标签
        assertEquals(false, html.contains("<p>Item 1</p>"))
        assertContains(html, "Item 1")
    }

    @Test
    fun should_render_nested_blockquote() {
        val md = "> Outer\n> > Inner"
        val html = render(md)
        // 应有两层 blockquote
        val count = Regex("<blockquote>").findAll(html).count()
        assertEquals(2, count)
    }

    // ─────────────── 便捷 API ───────────────

    @Test
    fun should_work_with_static_render_method() {
        val parser = MarkdownParser()
        val doc = parser.parse("# Test")
        val html = HtmlRenderer.render(doc)
        assertContains(html, "<h1")
        assertContains(html, "Test")
    }

    @Test
    fun should_work_with_renderMarkdown_convenience() {
        val html = HtmlRenderer.renderMarkdown("# Test")
        assertContains(html, "<h1")
        assertContains(html, "Test")
    }
}
