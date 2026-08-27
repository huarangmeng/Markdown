package com.hrm.markdown.parser.streaming

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class StreamingParserTest {

    // ────── 基础流式解析 ──────

    @Test
    fun should_parse_single_heading_in_stream() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("# Hello World")
        val doc = parser.document
        val heading = doc.children.firstOrNull { it !is BlankLine }
        assertIs<Heading>(heading)
        parser.endStream()
    }

    @Test
    fun should_parse_incremental_heading() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("# He")
        parser.append("llo")
        parser.append(" World")
        val doc = parser.document
        val heading = doc.children.firstOrNull { it !is BlankLine }
        assertIs<Heading>(heading)
        parser.endStream()
    }

    @Test
    fun should_parse_multiple_blocks_incrementally() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("# Title\n\n")
        parser.append("This is a paragraph.\n\n")
        parser.append("Another paragraph.")
        val doc = parser.document
        val blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.size >= 2, "Expected at least 2 blocks, got ${blocks.size}")
        assertIs<Heading>(blocks[0])
        parser.endStream()
    }

    @Test
    fun should_handle_empty_stream() {
        val parser = MarkdownParser()
        parser.beginStream()
        val doc = parser.endStream()
        assertTrue(doc.children.isEmpty() || doc.children.all { it is BlankLine })
    }

    @Test
    fun should_handle_empty_append() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("")
        parser.append("# Hello")
        val doc = parser.document
        val heading = doc.children.firstOrNull { it !is BlankLine }
        assertIs<Heading>(heading)
        parser.endStream()
    }

    // ────── 未关闭围栏代码块修复 ──────

    @Test
    fun should_auto_close_fenced_code_block() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("# Title\n\n")
        parser.append("```kotlin\n")
        parser.append("fun main() {\n")
        parser.append("    println(\"Hello\")\n")
        // 没有关闭 ```
        val doc = parser.document
        val blocks = doc.children.filter { it !is BlankLine }
        val codeBlock = blocks.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(codeBlock)
        assertTrue(codeBlock.literal.contains("fun main()"))
        parser.endStream()
    }

    @Test
    fun should_close_fenced_code_block_when_delimiter_arrives() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("```python\n")
        parser.append("print('hello')\n")
        // 还没关闭
        var doc = parser.document
        val openBlock = doc.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(openBlock)

        // 关闭围栏到达
        parser.append("```\n")
        doc = parser.document
        val closedBlock = doc.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(closedBlock)
        assertTrue(closedBlock.literal.contains("print('hello')"))
        parser.endStream()
    }

    // ────── 未关闭数学块修复 ──────

    @Test
    fun should_auto_close_math_block() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("$$\n")
        parser.append("\\int_0^1 f(x) dx\n")
        // 没有关闭 $$
        val doc = parser.document
        val blocks = doc.children.filter { it !is BlankLine }
        val mathBlock = blocks.find { it is MathBlock }
        assertIs<MathBlock>(mathBlock)
        assertTrue(mathBlock.literal.contains("\\int_0^1"))
        parser.endStream()
    }

    @Test
    fun should_reuse_math_block_instance_during_streaming_append() {
        val parser = MarkdownParser()
        parser.beginStream()

        val firstDoc = parser.append("$$ x")
        val firstMath = firstDoc.children.firstOrNull { it !is BlankLine }
        assertIs<MathBlock>(firstMath)

        val secondDoc = parser.append(" + y")
        val secondMath = secondDoc.children.firstOrNull { it !is BlankLine }
        assertIs<MathBlock>(secondMath)
        assertSame(firstMath, secondMath)
        assertEquals("x + y", secondMath.literal)

        val closedDoc = parser.append(" $$\nnext")
        val closedMath = closedDoc.children.firstOrNull { it !is BlankLine }
        assertIs<MathBlock>(closedMath)
        assertSame(firstMath, closedMath)
        assertEquals("x + y ", closedMath.literal)

        parser.endStream()
    }

    // ────── 未关闭行内强调修复 ──────

    @Test
    fun should_auto_close_bold() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("This is **bold text")
        val doc = parser.document
        // bold text 应该被正确渲染为粗体
        val para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        val hasStrong = para.children.any { it is StrongEmphasis }
        assertTrue(hasStrong, "Expected StrongEmphasis node in paragraph")
        parser.endStream()
    }

    @Test
    fun should_auto_close_italic() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("This is *italic text")
        val doc = parser.document
        val para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        val hasEmphasis = para.children.any { it is Emphasis }
        assertTrue(hasEmphasis, "Expected Emphasis node in paragraph")
        parser.endStream()
    }

    @Test
    fun should_auto_close_inline_code() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("Use the `print function")
        val doc = parser.document
        val para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        val hasCode = para.children.any { it is InlineCode }
        assertTrue(hasCode, "Expected InlineCode node in paragraph")
        parser.endStream()
    }

    @Test
    fun should_auto_close_strikethrough() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("This is ~~deleted text")
        val doc = parser.document
        val para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        val hasStrike = para.children.any { it is Strikethrough }
        assertTrue(hasStrike, "Expected Strikethrough node in paragraph")
        parser.endStream()
    }

    @Test
    fun should_auto_close_highlight() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("This is ==highlighted text")
        val doc = parser.document
        val para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        val hasHighlight = para.children.any { it is Highlight }
        assertTrue(hasHighlight, "Expected Highlight node in paragraph")
        parser.endStream()
    }

    @Test
    fun should_not_extend_temporary_inline_math_to_next_line_text() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("其中，\$(h, k)\n")
        parser.append("为什么抛物线的函数可以表示为 y = a(x - h)^2 + k ？")

        val doc = parser.document
        val para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)

        val mathNodes = para.children.filterIsInstance<InlineMath>()
        assertTrue(mathNodes.isNotEmpty(), "Expected temporary inline math node")

        parser.endStream()
    }

    // ────── 未关闭链接修复 ──────

    @Test
    fun should_auto_close_link_url() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("See [docs](https://example.com")
        val doc = parser.document
        val para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        val hasLink = para.children.any { it is Link }
        assertTrue(hasLink, "Expected Link node in paragraph")
        parser.endStream()
    }

    // ────── 嵌套结构修复 ──────

    @Test
    fun should_auto_close_nested_emphasis() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("**bold and *italic")
        val doc = parser.document
        val para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        // 修复后应该有强调节点（Emphasis 或 StrongEmphasis）
        val hasEmphasis = para.children.any { it is Emphasis || it is StrongEmphasis }
        assertTrue(hasEmphasis, "Expected emphasis nodes in nested structure")
        parser.endStream()
    }

    // ────── 块引用中的修复 ──────

    @Test
    fun should_auto_close_in_block_quote() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("> This is **bold text")
        val doc = parser.document
        val bq = doc.children.firstOrNull { it is BlockQuote } as? BlockQuote
        assertIs<BlockQuote>(bq)
        parser.endStream()
    }

    // ────── 列表中的修复 ──────

    @Test
    fun should_auto_close_in_list() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("- Item with **bold")
        val doc = parser.document
        val list = doc.children.firstOrNull { it is ListBlock }
        assertIs<ListBlock>(list)
        parser.endStream()
    }

    // ────── 流控制 ──────

    @Test
    fun should_track_streaming_state() {
        val parser = MarkdownParser()
        assertFalse(parser.isStreaming)
        parser.beginStream()
        assertTrue(parser.isStreaming)
        parser.append("# Hello")
        assertTrue(parser.isStreaming)
        parser.endStream()
        assertFalse(parser.isStreaming)
    }

    @Test
    fun should_handle_abort() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("# Hello\n\n```kotlin\nfun main() {")
        val doc = parser.abort()
        assertFalse(parser.isStreaming)
        assertTrue(doc.children.isNotEmpty())
    }

    // ────── 非流式模式兼容 ──────

    @Test
    fun should_parse_full_document() {
        val parser = MarkdownParser()
        val doc = parser.parse("# Hello\n\nWorld")
        val blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.size >= 2)
        assertIs<Heading>(blocks[0])
        assertIs<Paragraph>(blocks[1])
    }

    @Test
    fun should_parse_complex_document() {
        val parser = MarkdownParser()
        val doc = parser.parse("""
            # Title
            
            This is a paragraph with **bold** and *italic* text.
            
            - Item 1
            - Item 2
            
            ```kotlin
            fun hello() = println("Hello")
            ```
            
            > A block quote
        """.trimIndent())
        val blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.size >= 4, "Expected at least 4 blocks, got ${blocks.size}")
    }

    // ────── endStream 后不再做修复 ──────

    @Test
    fun should_not_repair_after_end_stream() {
        val parser = MarkdownParser()
        parser.beginStream()
        parser.append("This is **bold")
        // 流式中：应修复
        var doc = parser.document
        var para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        val hasStrongDuringStream = para.children.any { it is StrongEmphasis }
        assertTrue(hasStrongDuringStream, "Expected repair during streaming")

        // 结束流：最终全量解析，无修复
        doc = parser.endStream()
        para = doc.children.firstOrNull { it is Paragraph } as? Paragraph
        assertIs<Paragraph>(para)
        // 原始 CommonMark 行为：未关闭的 ** 变为纯文本
        // endStream 后不做修复，直接按标准解析
    }

    // ────── 大段文本增量 ──────

    @Test
    fun should_handle_large_incremental_output() {
        val parser = MarkdownParser()
        parser.beginStream()

        // 模拟 LLM 逐步输出
        parser.append("# Chapter 1\n\n")
        parser.append("This is the first paragraph. ")
        parser.append("It has multiple sentences.\n\n")
        parser.append("## Section 1.1\n\n")
        parser.append("Another paragraph here.\n\n")
        parser.append("```kotlin\n")
        parser.append("fun main() {\n")
        parser.append("    println(\"Hello\")\n")
        parser.append("}\n")
        parser.append("```\n\n")
        parser.append("Back to normal text.")

        val doc = parser.endStream()
        val blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.size >= 5, "Expected at least 5 blocks, got ${blocks.size}")
    }

    // ────── 流式过程中块识别一致性 ──────

    @Test
    fun should_identify_list_correctly_during_streaming() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("# Title\n\n")
        parser.append("- Item 1\n")

        // 过程中应该识别为 ListBlock
        var doc = parser.document
        var blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.any { it is ListBlock }, "Expected ListBlock during streaming, got: ${blocks.map { it::class.simpleName }}")

        parser.append("- Item 2\n")
        doc = parser.document
        blocks = doc.children.filter { it !is BlankLine }
        val list = blocks.find { it is ListBlock } as? ListBlock
        assertIs<ListBlock>(list)
        assertTrue(list.children.size >= 2, "Expected at least 2 items, got ${list.children.size}")

        val finalDoc = parser.endStream()
        val finalBlocks = finalDoc.children.filter { it !is BlankLine }
        val finalList = finalBlocks.find { it is ListBlock } as? ListBlock
        assertIs<ListBlock>(finalList)
        assertEquals(2, finalList.children.size)
    }

    @Test
    fun should_identify_table_correctly_during_streaming() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("# Title\n\n")
        parser.append("| Header 1 | Header 2 |\n")
        parser.append("| --- | --- |\n")

        // 过程中应该识别为 Table
        var doc = parser.document
        var blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.any { it is Table }, "Expected Table during streaming after header+delimiter, got: ${blocks.map { it::class.simpleName }}")

        parser.append("| Cell 1 | Cell 2 |\n")
        doc = parser.document
        blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.any { it is Table }, "Expected Table during streaming after adding row")

        val finalDoc = parser.endStream()
        val finalBlocks = finalDoc.children.filter { it !is BlankLine }
        assertTrue(finalBlocks.any { it is Table }, "Expected Table after endStream")
    }

    @Test
    fun should_identify_code_block_correctly_during_streaming() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("Some text\n\n")
        parser.append("```python\n")

        // 过程中应该有 FencedCodeBlock
        var doc = parser.document
        var blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.any { it is FencedCodeBlock }, "Expected FencedCodeBlock during streaming, got: ${blocks.map { it::class.simpleName }}")

        parser.append("def hello():\n")
        parser.append("    print('hi')\n")
        doc = parser.document
        blocks = doc.children.filter { it !is BlankLine }
        val codeBlock = blocks.find { it is FencedCodeBlock } as? FencedCodeBlock
        assertIs<FencedCodeBlock>(codeBlock)
        assertTrue(codeBlock.literal.contains("def hello()"), "Code block should contain the code")

        // 关闭围栏
        parser.append("```\n\n")
        parser.append("After code.")
        doc = parser.document
        blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.any { it is FencedCodeBlock }, "FencedCodeBlock should still exist after closing")
        assertTrue(blocks.any { it is Paragraph }, "Should have paragraph after code block")

        parser.endStream()
    }

    @Test
    fun should_match_streaming_and_final_parse_for_heading_then_list() {
        val parser = MarkdownParser()
        parser.beginStream()

        // 模拟逐 token 输出：标题 + 列表
        val tokens = listOf(
            "##", " ", "Features", "\n\n",
            "-", " ", "Fast", "\n",
            "-", " ", "Reliable", "\n",
            "-", " ", "Easy to use", "\n"
        )
        for (token in tokens) {
            parser.append(token)
        }

        // 流式中间状态检查
        val streamDoc = parser.document
        val streamBlocks = streamDoc.children.filter { it !is BlankLine }
        assertTrue(streamBlocks.any { it is Heading }, "Expected Heading during streaming")
        assertTrue(streamBlocks.any { it is ListBlock }, "Expected ListBlock during streaming")

        // 最终解析
        val finalDoc = parser.endStream()
        val finalBlocks = finalDoc.children.filter { it !is BlankLine }
        assertIs<Heading>(finalBlocks[0])
        val list = finalBlocks.find { it is ListBlock } as? ListBlock
        assertIs<ListBlock>(list)
        assertEquals(3, list.children.size)
    }

    @Test
    fun should_match_streaming_and_final_parse_for_mixed_blocks() {
        // 模拟一个典型 LLM 输出场景：标题 → 段落 → 代码块 → 列表
        val fullInput = """
            # Getting Started

            Install the package:

            ```bash
            npm install markdown-parser
            ```

            Features:

            - Fast parsing
            - Streaming support
            - Auto-close repair
        """.trimIndent()

        // 先做全量解析作为参考
        val refParser = MarkdownParser()
        val refDoc = refParser.parse(fullInput)
        val refBlocks = refDoc.children.filter { it !is BlankLine }

        // 再做流式解析
        val streamParser = MarkdownParser()
        streamParser.beginStream()

        // 逐行喂入
        for (line in fullInput.lines()) {
            streamParser.append(line + "\n")
        }

        val finalDoc = streamParser.endStream()
        val finalBlocks = finalDoc.children.filter { it !is BlankLine }

        // 流式结束后的块结构应该和全量解析一致
        assertEquals(
            refBlocks.size,
            finalBlocks.size,
            "Block count mismatch: ref=${refBlocks.map { it::class.simpleName }}, streaming=${finalBlocks.map { it::class.simpleName }}"
        )
        for (i in refBlocks.indices) {
            assertEquals(
                refBlocks[i]::class,
                finalBlocks[i]::class,
                "Block type mismatch at index $i: ref=${refBlocks[i]::class.simpleName}, streaming=${finalBlocks[i]::class.simpleName}"
            )
        }
    }

    @Test
    fun should_handle_block_quote_with_list_during_streaming() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("> Note:\n")
        parser.append("> - item 1\n")
        parser.append("> - item 2\n")

        val doc = parser.document
        val blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.any { it is BlockQuote }, "Expected BlockQuote during streaming, got: ${blocks.map { it::class.simpleName }}")

        parser.endStream()
    }

    @Test
    fun should_handle_heading_followed_by_code_block_no_blank_line() {
        // 测试标题后紧接代码块（无空行分隔）的情况
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("# Code Example\n")
        parser.append("```\n")
        parser.append("hello world\n")

        val doc = parser.document
        val blocks = doc.children.filter { it !is BlankLine }
        assertTrue(blocks.any { it is Heading }, "Expected Heading, got: ${blocks.map { it::class.simpleName }}")
        assertTrue(blocks.any { it is FencedCodeBlock }, "Expected FencedCodeBlock, got: ${blocks.map { it::class.simpleName }}")

        parser.endStream()
    }

    @Test
    fun should_handle_paragraph_continuation_correctly() {
        // 段落续行：新行不以块标记开头时应合并到当前段落
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("This is a long paragraph ")
        parser.append("that spans multiple ")
        parser.append("tokens without newlines.\n\n")
        parser.append("Second paragraph.")

        val doc = parser.document
        val blocks = doc.children.filter { it !is BlankLine }
        val paragraphs = blocks.filterIsInstance<Paragraph>()
        assertTrue(paragraphs.size >= 2, "Expected at least 2 paragraphs, got ${paragraphs.size}")

        parser.endStream()
    }

    @Test
    fun should_not_include_language_in_code_literal_during_streaming() {
        // 模拟 LLM 逐 token 输出代码块，语言标识不应出现在 literal 中
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("Some text\n\n")
        parser.append("```")
        parser.append("kotlin")
        parser.append("\n")
        parser.append("class Counter")
        parser.append("ViewModel {\n")
        parser.append("    private ")
        parser.append("val _count ")
        parser.append("= Mutable")
        parser.append("StateFlow(0)\n")

        // 在流式过程中检查
        var doc = parser.document
        var blocks = doc.children.filter { it !is BlankLine }
        val codeBlock = blocks.find { it is FencedCodeBlock } as? FencedCodeBlock
        assertIs<FencedCodeBlock>(codeBlock)
        // 关键断言：literal 不应包含 "kotlin" 作为第一行
        assertFalse(
            codeBlock.literal.startsWith("kotlin"),
            "Code block literal should NOT start with language identifier 'kotlin', but got: '${codeBlock.literal.take(50)}'"
        )
        assertEquals("kotlin", codeBlock.language, "Language should be 'kotlin'")

        // 继续输出并关闭
        parser.append("}\n")
        parser.append("```\n\n")
        parser.append("After code.")

        val finalDoc = parser.endStream()
        val finalBlocks = finalDoc.children.filter { it !is BlankLine }
        val finalCodeBlock = finalBlocks.find { it is FencedCodeBlock } as? FencedCodeBlock
        assertIs<FencedCodeBlock>(finalCodeBlock)
        assertFalse(
            finalCodeBlock.literal.startsWith("kotlin"),
            "Final code block literal should NOT start with 'kotlin', got: '${finalCodeBlock.literal.take(50)}'"
        )
        assertEquals("kotlin", finalCodeBlock.language)
    }

    @Test
    fun should_not_include_language_in_code_literal_normal_parse() {
        // 全量解析也验证一下
        val parser = MarkdownParser()
        val doc = parser.parse("Some text\n\n```kotlin\nclass Foo {\n}\n```\n")
        val blocks = doc.children.filter { it !is BlankLine }
        val codeBlock = blocks.find { it is FencedCodeBlock } as? FencedCodeBlock
        assertIs<FencedCodeBlock>(codeBlock)
        assertFalse(
            codeBlock.literal.startsWith("kotlin"),
            "Normal parse: literal should NOT start with 'kotlin', got: '${codeBlock.literal.take(80)}'"
        )
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.literal.contains("class Foo"), "Literal should contain the code")
    }

    @Test
    fun should_identify_admonition_during_streaming() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("# Tips\n\n")
        parser.append("> [!NOTE]\n")
        parser.append("> This is important.\n")

        val doc = parser.document
        val blocks = doc.children.filter { it !is BlankLine }
        // 应该有标题和某种块引用结构（Admonition 或 BlockQuote）
        assertTrue(blocks.any { it is Heading }, "Expected Heading")
        assertTrue(
            blocks.any { it is Admonition || it is BlockQuote },
            "Expected Admonition or BlockQuote, got: ${blocks.map { it::class.simpleName }}"
        )

        parser.endStream()
    }

    // ────── 节点实例复用测试 ──────

    @Test
    fun should_reuse_fenced_code_block_instance_across_appends() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("```kotlin\n")
        parser.append("fun main() {\n")

        // 获取第一次的 FencedCodeBlock 实例引用
        val doc1 = parser.document
        val codeBlock1 = doc1.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(codeBlock1)

        // 继续追加 token（代码块仍未闭合）
        parser.append("    println(\"Hello\")\n")

        val doc2 = parser.document
        val codeBlock2 = doc2.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(codeBlock2)

        // 关键断言：同一个未闭合代码块在连续 append 中应复用实例引用
        assertTrue(
            codeBlock1 === codeBlock2,
            "FencedCodeBlock instance should be reused across appends (=== identity)"
        )

        // 内容应该已更新
        assertTrue(
            codeBlock2.literal.contains("println"),
            "Reused instance should have updated literal"
        )

        parser.endStream()
    }

    @Test
    fun should_reuse_fenced_code_block_instance_with_multiple_tokens() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("```\n")
        parser.append("line1\n")

        val doc1 = parser.document
        val ref1 = doc1.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(ref1)

        parser.append("line2\n")
        val ref2 = parser.document.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(ref2)

        parser.append("line3\n")
        val ref3 = parser.document.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(ref3)

        parser.append("line4\n")
        val ref4 = parser.document.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(ref4)

        // 所有引用应该是同一个实例
        assertTrue(ref1 === ref2, "ref1 === ref2")
        assertTrue(ref2 === ref3, "ref2 === ref3")
        assertTrue(ref3 === ref4, "ref3 === ref4")

        // 最终内容应包含所有行
        assertTrue(ref4.literal.contains("line1"), "Should contain line1")
        assertTrue(ref4.literal.contains("line4"), "Should contain line4")

        parser.endStream()
    }

    @Test
    fun should_not_reuse_instance_after_code_block_closed() {
        val parser = MarkdownParser()
        parser.beginStream()

        parser.append("```\n")
        parser.append("code content\n")

        val doc1 = parser.document
        val openBlock = doc1.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(openBlock)

        // 关闭代码块 + 空行（使其变为 stable）
        parser.append("```\n\n")

        val doc2 = parser.document
        val closedBlock = doc2.children.find { it is FencedCodeBlock }
        assertIs<FencedCodeBlock>(closedBlock)

        // 关闭后的代码块内容应该正确
        assertTrue(
            closedBlock.literal.contains("code content"),
            "Closed block should contain the code content"
        )

        parser.endStream()
    }

}

class InlineAutoCloserTest {

    @Test
    fun should_return_empty_for_complete_content() {
        assertEquals("", InlineAutoCloser.buildRepairSuffix("Hello world"))
        assertEquals("", InlineAutoCloser.buildRepairSuffix("**bold** text"))
        assertEquals("", InlineAutoCloser.buildRepairSuffix("*italic* text"))
        assertEquals("", InlineAutoCloser.buildRepairSuffix("`code` text"))
    }

    @Test
    fun should_return_empty_for_empty_content() {
        assertEquals("", InlineAutoCloser.buildRepairSuffix(""))
    }

    @Test
    fun should_repair_unclosed_bold() {
        val suffix = InlineAutoCloser.buildRepairSuffix("This is **bold")
        assertTrue(suffix.contains("**"), "Expected ** in suffix, got: $suffix")
    }

    @Test
    fun should_repair_unclosed_italic() {
        val suffix = InlineAutoCloser.buildRepairSuffix("This is *italic")
        assertTrue(suffix.contains("*"), "Expected * in suffix, got: $suffix")
    }

    @Test
    fun should_repair_unclosed_backtick() {
        val suffix = InlineAutoCloser.buildRepairSuffix("Use the `print")
        assertTrue(suffix.contains("`"), "Expected ` in suffix, got: $suffix")
    }

    @Test
    fun should_repair_unclosed_strikethrough() {
        val suffix = InlineAutoCloser.buildRepairSuffix("This is ~~deleted")
        assertTrue(suffix.contains("~~"), "Expected ~~ in suffix, got: $suffix")
    }

    @Test
    fun should_repair_unclosed_highlight() {
        val suffix = InlineAutoCloser.buildRepairSuffix("This is ==highlighted")
        assertTrue(suffix.contains("=="), "Expected == in suffix, got: $suffix")
    }

    @Test
    fun should_repair_unclosed_link_url() {
        val suffix = InlineAutoCloser.buildRepairSuffix("See [docs](https://example.com")
        assertTrue(suffix.contains(")"), "Expected ) in suffix, got: $suffix")
    }

    @Test
    fun should_repair_nested_emphasis() {
        val suffix = InlineAutoCloser.buildRepairSuffix("**bold and *italic")
        // 应该关闭 * 和 **
        assertTrue(suffix.contains("*"), "Expected * in suffix, got: $suffix")
    }

    @Test
    fun should_not_repair_escaped() {
        val suffix = InlineAutoCloser.buildRepairSuffix("This is \\*not italic")
        assertEquals("", suffix, "Should not repair escaped delimiter")
    }

    @Test
    fun should_repair_unclosed_inline_math() {
        val suffix = InlineAutoCloser.buildRepairSuffix("Formula: \$E=mc^2")
        assertTrue(suffix.contains("$"), "Expected \$ in suffix, got: $suffix")
    }

    @Test
    fun should_not_repair_closed_inline_math_ending_with_digit() {
        // Issue #43: 数字结尾的闭合公式 $m/s^2$ 不应被误判为未闭合
        val suffix = InlineAutoCloser.buildRepairSuffix("its SI unit becomes \$m/s^2\$.")
        assertEquals("", suffix, "Complete inline math should not need repair, got: $suffix")
    }

    @Test
    fun should_not_repair_closed_inline_math_with_superscript_number() {
        // 闭合符前是数字（^2 的 2）时仍应正确闭合
        val suffix = InlineAutoCloser.buildRepairSuffix("energy \$E=mc^2\$ is famous")
        assertEquals("", suffix, "Complete inline math should not need repair, got: $suffix")
    }

    @Test
    fun should_treat_price_dollar_as_literal_not_math_opener() {
        // 前导数字的 $（价格）不应被当作公式起始，从而不产生修复后缀
        assertEquals("", InlineAutoCloser.buildRepairSuffix("It costs 100\$ today"))
    }

    @Test
    fun should_not_open_math_when_dollar_followed_by_whitespace() {
        // "$ " 后接空白，不作为公式起始
        assertEquals("", InlineAutoCloser.buildRepairSuffix("price is \$ 5 dollars"))
    }

    @Test
    fun should_still_repair_genuinely_unclosed_math_ending_with_digit() {
        // 真正未闭合、且以数字结尾的公式仍需补全
        val suffix = InlineAutoCloser.buildRepairSuffix("becomes \$m/s^2")
        assertTrue(suffix.contains("$"), "Expected \$ in suffix, got: $suffix")
    }

    @Test
    fun should_repair_unclosed_superscript() {
        val suffix = InlineAutoCloser.buildRepairSuffix("x^2")
        assertTrue(suffix.contains("^"), "Expected ^ in suffix, got: $suffix")
    }

    @Test
    fun should_repair_unclosed_inserted_text() {
        val suffix = InlineAutoCloser.buildRepairSuffix("This is ++inserted")
        assertTrue(suffix.contains("++"), "Expected ++ in suffix, got: $suffix")
    }

    @Test
    fun should_handle_complete_link() {
        val suffix = InlineAutoCloser.buildRepairSuffix("[text](url)")
        assertEquals("", suffix, "Complete link should not need repair")
    }

    @Test
    fun should_handle_multiple_unclosed() {
        val suffix = InlineAutoCloser.buildRepairSuffix("**bold and `code")
        // 应该同时关闭 ` 和 **
        assertTrue(suffix.contains("`"), "Expected ` in suffix")
        assertTrue(suffix.contains("**"), "Expected ** in suffix")
    }
}
