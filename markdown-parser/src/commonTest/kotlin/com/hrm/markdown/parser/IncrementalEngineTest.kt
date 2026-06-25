package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.incremental.EditOperation
import com.hrm.markdown.parser.incremental.IncrementalEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 增量解析引擎和编辑功能测试。
 */
class IncrementalEngineTest {

    // ────── 全量解析（通过引擎） ──────

    @Test
    fun should_parse_full_document_via_engine() {
        val engine = IncrementalEngine()
        val doc = engine.fullParse("# Hello\n\nWorld")
        assertEquals(2, doc.children.size)
        assertTrue(doc.children[0] is Heading)
        assertTrue(doc.children[1] is Paragraph)
    }

    // ────── 编辑 API 测试 ──────

    @Test
    fun should_insert_text_and_reparse() {
        val parser = MarkdownParser()
        parser.parse("# Hello\n\nWorld")

        // 在 "World" 后插入更多文本
        val doc = parser.insert(offset = 14, text = " of Markdown")
        val para = doc.children.last()
        assertTrue(para is Paragraph)
    }

    @Test
    fun should_delete_text_and_reparse() {
        val parser = MarkdownParser()
        parser.parse("# Hello\n\nWorld\n\nExtra")

        // 删除 "World\n\n" 部分
        val doc = parser.delete(offset = 9, length = 7)
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_replace_text_and_reparse() {
        val parser = MarkdownParser()
        parser.parse("# Hello\n\nWorld")

        // 将 "# Hello" 替换为 "## Goodbye"
        val doc = parser.replace(offset = 0, length = 7, newText = "## Goodbye")
        val heading = doc.children.first()
        assertTrue(heading is Heading)
        assertEquals(2, heading.level)
    }

    @Test
    fun should_handle_edit_after_stream_parse() {
        val parser = MarkdownParser()
        // 先做全量解析
        parser.parse("# Title\n\nParagraph one")
        // 然后用编辑 API 添加内容
        val doc = parser.insert(offset = 21, text = "\n\nParagraph two")
        assertTrue(doc.children.size >= 2)
    }

    @Test
    fun should_handle_empty_after_delete() {
        val parser = MarkdownParser()
        parser.parse("Hello")
        val doc = parser.delete(offset = 0, length = 5)
        assertTrue(doc.children.isEmpty() || doc.children.all { it is BlankLine })
    }

    @Test
    fun should_apply_edit_operation_directly() {
        val parser = MarkdownParser()
        parser.parse("# Hello\n\nWorld")
        val doc = parser.applyEdit(EditOperation.Insert(14, "\n\n## New Section"))
        assertTrue(doc.children.any { it is Heading && it.level == 2 })
    }

    // ────── IncrementalEngine 流式测试 ──────

    @Test
    fun should_stream_via_incremental_engine() {
        val engine = IncrementalEngine()
        engine.beginStream()
        engine.append("# Hello")
        engine.append(" World\n\n")
        engine.append("This is text")
        val doc = engine.endStream()
        assertTrue(doc.children.isNotEmpty())
        assertTrue(doc.children[0] is Heading)
    }

    @Test
    fun should_handle_abort() {
        val engine = IncrementalEngine()
        engine.beginStream()
        engine.append("# Hello")
        engine.append("\n\nPartial **bold")
        val doc = engine.abort()
        // abort 后应该有有效文档
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_not_drop_previous_list_items_when_streaming_nested_list_marker() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("1. **公司背景**  \n")
        parser.append("   - DeepSeek 由国内顶尖的 AI 研究团队创立。\n\n")

        parser.append("2. **主营业务与产品**  \n")
        // 中间态：只到达缩进 + '-'（历史上这里会闪一下并可能导致前面的项被裁掉）
        val interim = parser.append("   -")
        val final = parser.append(" 专注于开发高性能、低成本的大语言模型。\n\n")

        val list1 = interim.children.filterIsInstance<ListBlock>().firstOrNull { it.ordered }
        assertTrue(list1 != null)
        assertTrue(list1.children.filterIsInstance<ListItem>().size >= 2)
        assertTrue(!containsSetextHeading(interim))

        val list2 = final.children.filterIsInstance<ListBlock>().firstOrNull { it.ordered }
        assertTrue(list2 != null)
        assertEquals(2, list2.children.filterIsInstance<ListItem>().size)
    }

    @Test
    fun should_update_content_hash_and_plain_text_for_streaming_tail_append() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append(
            """
            **需要说明的是**:  
            - 如果你有具体的技术细节、商业模式或某款产品想深入讨论，我可以结
            """.trimIndent()
        )
        val midDoc = parser.append("")

        val finalDoc = parser.append("合已有知识进一步交流。")

        val midParagraph = findLastParagraph(midDoc)
        val finalParagraph = findLastParagraph(finalDoc)

        assertTrue(midParagraph != null)
        assertTrue(finalParagraph != null)
        assertTrue(extractPlainText(midParagraph).contains("我可以结"))
        assertTrue(extractPlainText(finalParagraph).contains("我可以结合已有知识进一步交流。"))
        assertTrue(midParagraph.contentHash != finalParagraph.contentHash)
    }

    @Test
    fun should_keep_reparsing_from_container_start_when_streaming_list_tail_grows() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append(
            """
            **需要说明的是**:  
            - 我的信息可能无法涵盖2025年5月之后的最新动态，如需了解该公司近期进展（如新模型发布、合作伙伴、融资情况等），建议查阅最新权威报道或官方渠道。  
            - 如果你有具体的技术细节、商业模式或某款产品想深入讨论，我可以结
            """.trimIndent()
        )

        val midDoc = parser.append("")
        val finalDoc = parser.append("合已有知识进一步交流。")

        val midParagraph = findLastParagraph(midDoc)
        val finalParagraph = findLastParagraph(finalDoc)

        assertTrue(midParagraph != null)
        assertTrue(finalParagraph != null)
        assertTrue(extractPlainText(midParagraph).contains("我可以结"))
        assertTrue(extractPlainText(finalParagraph).contains("我可以结合已有知识进一步交流。"))
    }

    // ────── 编辑场景：多次编辑 ──────

    @Test
    fun should_handle_multiple_sequential_edits() {
        val parser = MarkdownParser()
        parser.parse("# Title\n\nLine one")

        // 第一次编辑：在末尾追加
        val len1 = parser.currentText().length
        parser.insert(offset = len1, text = "\n\nLine two")

        // 第二次编辑：再追加
        val len2 = parser.currentText().length
        val doc = parser.insert(offset = len2, text = "\n\nLine three")
        assertTrue(doc.children.size >= 3)
    }

    @Test
    fun should_handle_insert_in_middle() {
        val parser = MarkdownParser()
        parser.parse("# Hello\n\nEnd")

        // 在段落之间插入新块
        val doc = parser.insert(offset = 9, text = "Middle\n\n")
        assertTrue(doc.children.size >= 2)
    }

    // ────── 编辑 + 块级结构 ──────

    @Test
    fun should_edit_inside_code_block() {
        val parser = MarkdownParser()
        parser.parse("```\ncode\n```\n\nText")
        // 在代码块内容中插入
        val doc = parser.insert(offset = 4, text = "more ")
        val codeBlock = doc.children.filterIsInstance<FencedCodeBlock>().firstOrNull()
        assertTrue(codeBlock != null)
        assertTrue(codeBlock.literal.contains("more "))
    }

    @Test
    fun should_edit_heading_level() {
        val parser = MarkdownParser()
        parser.parse("# Title\n\nText")
        // 将 # 替换为 ###
        val doc = parser.replace(offset = 0, length = 1, newText = "###")
        val heading = doc.children.first()
        assertTrue(heading is Heading)
        assertEquals(3, heading.level)
    }

    private fun containsSetextHeading(node: Node): Boolean {
        if (node is SetextHeading) return true
        return if (node is ContainerNode) {
            node.children.any { child -> containsSetextHeading(child) }
        } else {
            false
        }
    }

    private fun findLastParagraph(node: Node): Paragraph? {
        if (node is Paragraph) return node
        if (node is ContainerNode) {
            for (child in node.children.asReversed()) {
                val found = findLastParagraph(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun extractPlainText(node: Node): String = when (node) {
        is Text -> node.literal
        is InlineCode -> node.literal
        is SoftLineBreak -> " "
        is HardLineBreak -> "\n"
        is ContainerNode -> node.children.joinToString(separator = "") { child -> extractPlainText(child) }
        else -> ""
    }
}
