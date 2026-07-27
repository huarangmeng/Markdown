package com.hrm.markdown.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.Markdown

private val narrowBubbleMarkdown = """
这是一段放在聊天气泡里的长中文内容，包含中文逗号、顿号、括号（继续补充说明）、冒号：以及 EnglishWordsWithoutSpacesMixedWith中文，应该在窄宽度中自然换行。

这里还有 **加粗中文文本**、*斜体 English text*、`inlineCode()` 和连续标点！！！？？？用于确认不同 inline run 之间不会越过右侧边界。

- 第一层列表项包含很长的中文描述，并且紧跟多个标点，观察缩进后剩余宽度是否仍能正确换行。
  - 第二层列表项继续使用中英文混排 MixedTextWithoutSpaces 和中文标点（A、B、C）。
  - 另一个子项：长句子长句子长句子长句子长句子。
1. 有序项也放在同一个窄容器中，确认 marker 后面的行内文本不会冲出气泡。
""".trimIndent()

private val complexScriptMarkdown = """
भीमसेन थापा

पृथ्वीनारायण शाह

**जङ्गबहादुर राणा** — Devanagari mixed with Latin text.
""".trimIndent()

internal val inlineLayoutPreviewGroups = listOf(
    PreviewGroup(
        id = "overflow_regression",
        title = "越界回归",
        description = "窄容器换行及复杂文字字形裁剪回归",
        items = listOf(
            PreviewItem(
                id = "issue_29_narrow_bubble",
                title = "Issue #29 窄聊天气泡",
                markdown = narrowBubbleMarkdown,
                content = {
                    NarrowBubbleMarkdown(markdown = narrowBubbleMarkdown)
                }
            ),
            PreviewItem(
                id = "issue_34_devanagari_clipping",
                title = "Issue #34 天城文裁剪",
                markdown = complexScriptMarkdown,
                content = {
                    Markdown(markdown = complexScriptMarkdown)
                },
            ),
        )
    ),
)

@Composable
private fun NarrowBubbleMarkdown(markdown: String) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(10.dp)
        ) {
            Markdown(markdown = markdown)
        }
    }
}
