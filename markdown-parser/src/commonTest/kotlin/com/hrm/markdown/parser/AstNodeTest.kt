package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFalse

// container node — tree manipulation methods

class ContainerNodeManipulationTest {

    private val parser = MarkdownParser()

    private fun makeParagraph(text: String): Paragraph {
        val doc = parser.parse(text)
        return doc.children.first() as Paragraph
    }

    @Test
    fun should_append_child_and_set_parent() {
        val doc = parser.parse("hello")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertSame(doc, para.parent)
    }

    @Test
    fun should_report_correct_child_count() {
        val doc = parser.parse("**bold** and *italic*")
        val para = doc.children.first() as Paragraph
        assertTrue(para.childCount() >= 3)
    }

    @Test
    fun should_insert_child_at_index() {
        val doc = parser.parse("text")
        val heading = Heading(1)
        doc.insertChild(0, heading)
        assertIs<Heading>(doc.children.first())
        assertSame(doc, heading.parent)
    }

    @Test
    fun should_remove_child() {
        val doc = parser.parse("# Heading\n\nParagraph")
        val heading = doc.children.first()
        val removed = doc.removeChild(heading)
        assertTrue(removed)
        assertNull(heading.parent)
        assertTrue(doc.children.none { it is Heading })
    }

    @Test
    fun should_remove_child_at_index() {
        val doc = parser.parse("# Heading\n\nParagraph")
        val removed = doc.removeChildAt(0)
        assertIs<Heading>(removed)
        assertNull(removed.parent)
    }

    @Test
    fun should_replace_child() {
        val doc = parser.parse("# Heading\n\nParagraph")
        val oldHeading = doc.children.first()
        val newPara = Paragraph()
        doc.replaceChild(oldHeading, newPara)
        assertNull(oldHeading.parent)
        assertSame(doc, newPara.parent)
        assertIs<Paragraph>(doc.children.first())
    }

    @Test
    fun should_clear_all_children() {
        val doc = parser.parse("# Heading\n\nParagraph\n\n- Item")
        assertTrue(doc.children.isNotEmpty())
        val oldChildren = doc.children.toList()
        doc.clearChildren()
        assertTrue(doc.children.isEmpty())
        oldChildren.forEach { assertNull(it.parent) }
    }

    @Test
    fun should_replace_children_range() {
        val doc = parser.parse("Para 1\n\nPara 2\n\nPara 3")
        val original = doc.children.size
        val newHeading = Heading(2)
        doc.replaceChildren(0, 1, listOf(newHeading))
        assertIs<Heading>(doc.children.first())
        assertSame(doc, newHeading.parent)
    }

    @Test
    fun should_not_crash_on_removing_nonexistent_child() {
        val doc = parser.parse("hello")
        val orphan = Paragraph()
        val result = doc.removeChild(orphan)
        assertFalse(result)
    }
}

// node visitor — dispatch to correct visit methods

class NodeVisitorDispatchTest {

    private val parser = MarkdownParser()

    @Test
    fun should_dispatch_heading_to_visit_heading() {
        val doc = parser.parse("# Test")
        val heading = doc.children.first()
        assertIs<Heading>(heading)

        val visitor = object : DefaultNodeVisitor<String>("default") {
            override fun visitHeading(node: Heading) = "heading:${node.level}"
        }
        assertEquals("heading:1", heading.accept(visitor))
    }

    @Test
    fun should_dispatch_paragraph_to_visit_paragraph() {
        val doc = parser.parse("text")
        val para = doc.children.first()
        val visitor = object : DefaultNodeVisitor<String>("default") {
            override fun visitParagraph(node: Paragraph) = "paragraph"
        }
        assertEquals("paragraph", para.accept(visitor))
    }

    @Test
    fun should_dispatch_fenced_code_block() {
        val doc = parser.parse("```\ncode\n```")
        val code = doc.children.first()
        val visitor = object : DefaultNodeVisitor<String>("default") {
            override fun visitFencedCodeBlock(node: FencedCodeBlock) = "code:${node.language}"
        }
        assertEquals("code:", code.accept(visitor))
    }

    @Test
    fun should_dispatch_text_node() {
        val doc = parser.parse("hello")
        val para = doc.children.first() as Paragraph
        val text = para.children.first()
        val visitor = object : DefaultNodeVisitor<String>("default") {
            override fun visitText(node: Text) = "text:${node.literal}"
        }
        assertEquals("text:hello", text.accept(visitor))
    }

    @Test
    fun should_return_default_for_unhandled_types() {
        val doc = parser.parse("---")
        val tb = doc.children.first()
        assertIs<ThematicBreak>(tb)
        val visitor = object : DefaultNodeVisitor<String>("fallback") {}
        assertEquals("fallback", tb.accept(visitor))
    }

    @Test
    fun should_dispatch_table_node() {
        val doc = parser.parse("| A |\n| --- |\n| 1 |")
        val table = doc.children.first()
        val visitor = object : DefaultNodeVisitor<Int>(0) {
            override fun visitTable(node: Table) = node.columnAlignments.size
        }
        assertEquals(1, table.accept(visitor))
    }

    @Test
    fun should_dispatch_blockquote_node() {
        val doc = parser.parse("> hello")
        val bq = doc.children.first()
        val visitor = object : DefaultNodeVisitor<Boolean>(false) {
            override fun visitBlockQuote(node: BlockQuote) = true
        }
        assertTrue(bq.accept(visitor))
    }

    @Test
    fun should_dispatch_list_block_node() {
        val doc = parser.parse("- item")
        val list = doc.children.first()
        val visitor = object : DefaultNodeVisitor<Boolean>(false) {
            override fun visitListBlock(node: ListBlock) = true
        }
        assertTrue(list.accept(visitor))
    }
}

// source position and range on parsed nodes

class NodeSourceInfoTest {

    private val parser = MarkdownParser()

    @Test
    fun should_have_valid_line_range_on_heading() {
        val doc = parser.parse("# Title")
        val heading = doc.children.first()
        assertTrue(heading.lineRange.lineCount > 0)
    }

    @Test
    fun should_have_valid_line_range_on_paragraph() {
        val doc = parser.parse("Some paragraph text")
        val para = doc.children.first()
        assertTrue(para.lineRange.lineCount > 0)
    }

    @Test
    fun should_have_ordered_line_ranges_across_blocks() {
        val doc = parser.parse("# Title\n\nParagraph\n\n## Subtitle")
        val ranges = doc.children.map { it.lineRange }
        for (i in 1 until ranges.size) {
            assertTrue(ranges[i].startLine >= ranges[i - 1].startLine)
        }
    }

    @Test
    fun should_have_source_range_on_nodes() {
        val doc = parser.parse("# Title\n\nParagraph")
        for (child in doc.children) {
            assertTrue(child.sourceRange.length >= 0)
        }
    }
}
