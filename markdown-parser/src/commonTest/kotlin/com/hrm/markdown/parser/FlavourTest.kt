package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.flavour.CommonMarkFlavour
import com.hrm.markdown.parser.flavour.ExtendedFlavour
import com.hrm.markdown.parser.flavour.GFMFlavour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Flavour 系统测试。
 *
 * 验证不同方言的语法支持和扩展机制。
 */
class FlavourTest {

    @Test
    fun should_use_commonmark_flavour() {
        val parser = MarkdownParser(CommonMarkFlavour)
        val doc = parser.parse("""
            # Heading
            
            Paragraph with **bold** and *italic*.
            
            - List item 1
            - List item 2
        """.trimIndent())

        assertTrue(doc.children[0] is Heading)
        assertTrue(doc.children[1] is Paragraph)
        assertTrue(doc.children[2] is ListBlock)
    }

    @Test
    fun should_use_gfm_flavour() {
        val parser = MarkdownParser(GFMFlavour)
        val doc = parser.parse("""
            # GFM Features
            
            ~~Strikethrough~~
            
            | Name | Age |
            | ---- | --- |
            | Bob  | 30  |
            
            - [x] Completed
            - [ ] Todo
        """.trimIndent())

        assertTrue(doc.children[0] is Heading)
        
        // 表格
        var foundTable = false
        for (child in doc.children) {
            if (child is Table) {
                foundTable = true
                break
            }
        }
        assertTrue(foundTable, "Should parse GFM table")
        
        // 任务列表
        var foundList = false
        for (child in doc.children) {
            if (child is ListBlock) {
                foundList = true
                break
            }
        }
        assertTrue(foundList, "Should parse task list")
    }

    @Test
    fun should_use_extended_flavour() {
        val parser = MarkdownParser(ExtendedFlavour)
        val doc = parser.parse("""
            # Extended Features
            
            ::: warning
            This is a custom container!
            :::
            
            ${'$'}${'$'}
            y = ax + b
            ${'$'}${'$'}
        """.trimIndent())

        assertTrue(doc.children[0] is Heading)
        
        // 自定义容器
        var foundContainer = false
        for (child in doc.children) {
            if (child is CustomContainer) {
                foundContainer = true
                assertEquals("warning", child.type)
                break
            }
        }
        assertTrue(foundContainer, "Should parse custom container")
        
        // 数学块
        var foundMathBlock = false
        for (child in doc.children) {
            if (child is MathBlock) {
                foundMathBlock = true
                break
            }
        }
        assertTrue(foundMathBlock, "Should parse math block")
    }

    @Test
    fun should_default_to_extended_flavour() {
        // 不指定 flavour 时，应使用 ExtendedFlavour
        val parser = MarkdownParser()
        val doc = parser.parse("""
            ::: tip
            Default flavour should support extended features
            :::
        """.trimIndent())

        val container = doc.children.filterIsInstance<CustomContainer>().firstOrNull()
        assertTrue(container != null, "Default parser should use ExtendedFlavour")
        assertEquals("tip", container.type)
    }

    @Test
    fun should_support_flavour_specific_options() {
        // CommonMark 不支持表格
        val commonMarkParser = MarkdownParser(CommonMarkFlavour)
        val commonMarkDoc = commonMarkParser.parse("""
            | Name | Age |
            | ---- | --- |
            | Bob  | 30  |
        """.trimIndent())
        
        // CommonMark 会把表格当成段落
        assertTrue(commonMarkDoc.children.size > 0)
        var hasParagraph = false
        for (child in commonMarkDoc.children) {
            if (child is Paragraph) {
                hasParagraph = true
                break
            }
        }
        assertTrue(hasParagraph)
        
        // GFM 支持表格
        val gfmParser = MarkdownParser(GFMFlavour)
        val gfmDoc = gfmParser.parse("""
            | Name | Age |
            | ---- | --- |
            | Bob  | 30  |
        """.trimIndent())
        
        // GFM 会正确解析表格
        var foundTable = false
        for (child in gfmDoc.children) {
            if (child is Table) {
                foundTable = true
                break
            }
        }
        assertTrue(foundTable, "GFM should parse table")
    }
}
