package com.hrm.markdown.preview

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformancePreviewTest {
    @Test
    fun should_generateUltraLongMixedDocument_when_buildingPerformancePreview() {
        val markdown = buildUltraLongMarkdown()

        assertTrue(markdown.length > 750_000, "Expected an ultra-long document, got ${markdown.length} characters")
        assertEquals(
            ULTRA_LONG_MARKDOWN_SECTION_COUNT,
            markdown.lineSequence().count { it.startsWith("## 第 ") },
        )
        assertContains(markdown, "## 第 1 章：长文档混合内容验证")
        assertContains(markdown, "## 第 1000 章：长文档混合内容验证")
        assertContains(markdown, "```kotlin")
        assertContains(markdown, "| 累计进度 | 1000 / 1000 |")
    }
}
