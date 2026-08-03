package com.hrm.markdown.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hrm.markdown.renderer.Markdown

internal const val ULTRA_LONG_MARKDOWN_SECTION_COUNT = 1_000

internal val performancePreviewGroups = listOf(
    PreviewGroup(
        id = "ultra_long_document",
        title = "超长文档",
        description = "1000 个混合语法章节，用于验证首次解析、渲染、滚动和内存表现",
        items = listOf(
            PreviewItem(
                id = "ultra_long_mixed_markdown",
                title = "超长 Markdown（1000 章节）",
                content = { UltraLongMarkdownPreview() },
            ),
        ),
    ),
)

@Composable
private fun UltraLongMarkdownPreview() {
    // Only allocate the stress-test document after entering this preview. Keeping it out of the
    // category's top-level initialization avoids slowing down normal preview app startup.
    val markdown = remember { buildUltraLongMarkdown() }
    Markdown(
        markdown = markdown,
        modifier = Modifier.fillMaxSize(),
    )
}

internal fun buildUltraLongMarkdown(
    sectionCount: Int = ULTRA_LONG_MARKDOWN_SECTION_COUNT,
): String = buildString(capacity = sectionCount * 1_000) {
    appendLine("# 超长 Markdown 性能验证文档")
    appendLine()
    appendLine(
        "本文档由预览模块在进入页面时生成，包含 $sectionCount 个章节。" +
            "内容混合了常见块级与行内语法，用于观察首次解析耗时、首屏渲染、长距离滚动、回收复用与内存占用。",
    )
    appendLine()
    appendLine("- 章节数：$sectionCount")
    appendLine("- 重点观察：加载指示器持续时间、滚动帧率、内存峰值和返回页面后的资源释放")
    appendLine("- 渲染模式：Markdown 默认 LazyColumn 虚拟化渲染")
    appendLine()
    appendLine("---")
    appendLine()

    repeat(sectionCount) { zeroBasedIndex ->
        val section = zeroBasedIndex + 1
        appendLine("## 第 $section 章：长文档混合内容验证")
        appendLine()
        appendLine(
            "这是第 **$section** 个性能验证章节。长文档不仅需要快速完成语法解析，也需要在滚动过程中保持稳定的布局与组合效率。" +
                "本段包含 **粗体**、*斜体*、~~删除线~~、`inlineCode($section)` 和 " +
                "[测试链接](https://example.com/performance?section=$section)，用于覆盖常见行内节点。",
        )
        appendLine()
        appendLine(
            "当内容规模持续增长时，解析器应维持可预测的时间与空间复杂度；渲染器则应只组合视口附近的块。" +
                "这段中英文混排文本用于验证自动换行：Compose Multiplatform Markdown renderer keeps scrolling smooth，" +
                "同时检查中文标点、English words、数字 $section 以及路径 `/performance/section/$section` 的边界处理。",
        )
        appendLine()
        appendLine("1. 记录章节 $section 首次进入视口时的帧耗时")
        appendLine("2. 快速滚动后检查标题、列表、表格与代码块是否完整")
        appendLine("3. 往返滚动并观察 LazyColumn 节点回收和内存曲线")
        appendLine()
        appendLine("> 性能采样点 $section：超长 Markdown 应保持内容顺序稳定，不出现重复、丢块、闪烁或滚动位置跳变。")
        appendLine()
        appendLine("| 指标 | 本章采样值 | 说明 |")
        appendLine("| :--- | ---: | :--- |")
        appendLine("| 章节编号 | $section | 用于确认长距离滚动后的内容位置 |")
        appendLine("| 累计进度 | $section / $sectionCount | 用于定位性能退化区间 |")
        appendLine("| 混合节点 | 7 | 标题、段落、样式、列表、引用、表格、代码 |")
        appendLine()
        appendLine("```kotlin")
        appendLine("val sample$section = PerformanceSample(")
        appendLine("    section = $section,")
        appendLine("    totalSections = $sectionCount,")
        appendLine("    message = \"Keep parsing and rendering predictable\",")
        appendLine(")")
        appendLine("```")
        appendLine()
        appendLine("---")
        appendLine()
    }
}
