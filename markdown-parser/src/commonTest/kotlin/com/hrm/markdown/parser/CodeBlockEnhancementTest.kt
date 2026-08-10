package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.html.HtmlRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * tests for fenced code block enhancements: hl_lines, linenums, startline.
 */
class CodeBlockEnhancementTest {

    private fun parse(input: String): Document {
        return MarkdownParser().parse(input)
    }

    private fun renderHtml(input: String): String {
        return HtmlRenderer.renderMarkdown(input)
    }

    // ---- highlight lines ----

    @Test
    fun should_parse_hl_lines_attribute() {
        val doc = parse("```python {hl_lines=\"1 3-5\"}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertEquals("python", codeBlock.language)
        assertEquals(2, codeBlock.highlightLines.size)
        assertEquals(1..1, codeBlock.highlightLines[0])
        assertEquals(3..5, codeBlock.highlightLines[1])
    }

    @Test
    fun should_parse_single_hl_line() {
        val doc = parse("```js {hl_lines=\"3\"}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertEquals(1, codeBlock.highlightLines.size)
        assertEquals(3..3, codeBlock.highlightLines[0])
    }

    @Test
    fun should_parse_highlight_alias_with_commas() {
        val doc = parse("```js {highlight=\"1,3-4\"}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertEquals(listOf(1..1, 3..4), codeBlock.highlightLines)
    }

    // ---- line numbers ----

    @Test
    fun should_parse_linenums_true() {
        val doc = parse("```python {linenums=true}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertTrue(codeBlock.showLineNumbers)
    }

    @Test
    fun should_parse_linenums_false() {
        val doc = parse("```python {linenums=false}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertFalse(codeBlock.showLineNumbers)
    }

    @Test
    fun should_parse_linenos_alias() {
        val doc = parse("```python {linenos=true}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertTrue(codeBlock.showLineNumbers)
    }

    @Test
    fun should_parse_lineNumbers_alias() {
        val doc = parse("```python {lineNumbers=true}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertTrue(codeBlock.showLineNumbers)
    }

    @Test
    fun should_enable_line_numbers_from_class() {
        val doc = parse("```python {.line-numbers}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertTrue(codeBlock.showLineNumbers)
    }

    @Test
    fun should_default_linenums_to_true() {
        val doc = parse("```python\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertTrue(codeBlock.showLineNumbers)
    }

    // ---- start line number ----

    @Test
    fun should_parse_startline() {
        val doc = parse("```python {startline=10}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertEquals(10, codeBlock.startLineNumber)
    }

    @Test
    fun should_default_startline_to_1() {
        val doc = parse("```python\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertEquals(1, codeBlock.startLineNumber)
    }

    // ---- combined attributes ----

    @Test
    fun should_parse_all_attributes_together() {
        val doc = parse("```python {hl_lines=\"1 3-5\" linenums=true startline=10}\ncode\n```")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertEquals("python", codeBlock.language)
        assertEquals(2, codeBlock.highlightLines.size)
        assertTrue(codeBlock.showLineNumbers)
        assertEquals(10, codeBlock.startLineNumber)
    }

    // ---- html rendering ----

    @Test
    fun should_render_hl_lines_data_attribute() {
        val html = renderHtml("```python {hl_lines=\"1 3-5\"}\ncode\n```")
        assertTrue(html.contains("data-hl-lines=\"1 3-5\""), "should have data-hl-lines attribute")
    }

    @Test
    fun should_render_linenums_data_attribute() {
        val html = renderHtml("```python {linenums=true}\ncode\n```")
        assertTrue(html.contains("data-linenums=\"true\""), "should have data-linenums attribute")
    }

    @Test
    fun should_render_startline_data_attribute() {
        val html = renderHtml("```python {startline=10}\ncode\n```")
        assertTrue(html.contains("data-startline=\"10\""), "should have data-startline attribute")
    }

    @Test
    fun should_not_render_default_attributes() {
        val html = renderHtml("```python\ncode\n```")
        assertFalse(html.contains("data-hl-lines"), "should not have data-hl-lines when not set")
        assertFalse(html.contains("data-linenums"), "standard HTML should not expose Compose defaults")
        assertFalse(html.contains("data-startline"), "should not have data-startline when 1")
    }

    @Test
    fun should_render_all_data_attributes_together() {
        val html = renderHtml("```python {hl_lines=\"1 3-5\" linenums=true startline=10}\ncode\n```")
        assertTrue(html.contains("data-hl-lines=\"1 3-5\""))
        assertTrue(html.contains("data-linenums=\"true\""))
        assertTrue(html.contains("data-startline=\"10\""))
        assertTrue(html.contains("<pre"))
        assertTrue(html.contains("language-python"))
    }

    // ---- tilde fences ----

    @Test
    fun should_work_with_tilde_fences() {
        val doc = parse("~~~python {hl_lines=\"2\" linenums=true}\ncode\n~~~")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().first()
        assertEquals("python", codeBlock.language)
        assertEquals(1, codeBlock.highlightLines.size)
        assertEquals(2..2, codeBlock.highlightLines[0])
        assertTrue(codeBlock.showLineNumbers)
    }
}
