package com.hrm.markdown.renderer.block

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.ast.IndentedCodeBlock
import com.hrm.markdown.parser.core.Attributes
import com.hrm.markdown.renderer.LocalMarkdownTheme
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.highlight.SyntaxColorScheme
import com.hrm.markdown.renderer.highlight.SyntaxHighlighter

/**
 * 围栏代码块渲染器 (``` 或 ~~~)
 *
 * 支持通过 info-string 的 `{...}` 属性语法控制：
 * - **title**: 标题栏，如 `{title="main.kt"}`
 * - **linenos / lineNumbers**: 行号显示
 * - **highlight / hl_lines**: 高亮指定行，如 `{highlight="2,5-7"}`
 */
@Composable
internal fun FencedCodeBlockRenderer(
    node: FencedCodeBlock,
    modifier: Modifier = Modifier,
) {
    CodeBlockText(
        text = node.literal.ifEmpty { " " },
        language = node.language,
        attributes = node.attributes,
        modifier = modifier,
    )
}

/**
 * 缩进代码块渲染器
 */
@Composable
internal fun IndentedCodeBlockRenderer(
    node: IndentedCodeBlock,
    modifier: Modifier = Modifier,
) {
    CodeBlockText(
        text = node.literal.ifEmpty { " " },
        language = "",
        attributes = Attributes(),
        modifier = modifier,
    )
}

// parses "2,5-7,10" into a set of line numbers (1-based)
private fun parseHighlightLines(spec: String): Set<Int> {
    if (spec.isBlank()) return emptySet()
    val result = mutableSetOf<Int>()
    for (part in spec.split(",")) {
        val trimmed = part.trim()
        if ('-' in trimmed) {
            val (a, b) = trimmed.split("-", limit = 2)
            val from = a.trim().toIntOrNull() ?: continue
            val to = b.trim().toIntOrNull() ?: continue
            result.addAll(from..to)
        } else {
            trimmed.toIntOrNull()?.let { result.add(it) }
        }
    }
    return result
}

@Composable
private fun CodeBlockText(
    text: String,
    language: String,
    attributes: Attributes,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMarkdownTheme.current
    val density = LocalDensity.current
    val colorScheme = theme.syntaxColorScheme

    val title = attributes.pairs["title"]
    val showLineNumbers = attributes.pairs["linenos"]?.toBooleanStrictOrNull() == true
            || attributes.pairs["lineNumbers"]?.toBooleanStrictOrNull() == true
            || attributes.classes.contains("line-numbers")
    val highlightLines = parseHighlightLines(
        attributes.pairs["highlight"] ?: attributes.pairs["hl_lines"] ?: ""
    )

    val annotatedString = remember(text, language, colorScheme) {
        if (language.isNotEmpty()) {
            SyntaxHighlighter.highlight(text, language, colorScheme)
        } else {
            AnnotatedString(text)
        }
    }

    var stableMinHeight by remember { mutableStateOf(0.dp) }
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.codeBlockCornerRadius))
            .clipToBounds()
            .background(theme.codeBlockBackground)
            .heightIn(min = stableMinHeight),
    ) {
        // title bar
        if (title != null) {
            CodeBlockTitleBar(title, language, theme)
        }

        // code area
        val lines = remember(text) { text.lines() }
        val lineCount = lines.size
        val highlightColor = theme.codeBlockHighlightLineColor

        if (showLineNumbers || highlightLines.isNotEmpty()) {
            // line numbers + code side by side
            Row(modifier = Modifier.fillMaxWidth()) {
                if (showLineNumbers) {
                    LineNumberGutter(lineCount, theme)
                }

                Box(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = annotatedString,
                        style = theme.codeBlockStyle,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScrollState)
                            .drawBehind {
                                if (highlightLines.isNotEmpty()) {
                                    val lineH = theme.codeBlockStyle.lineHeight.toPx()
                                    val padTop = theme.codeBlockPadding.toPx()
                                    for (lineNum in highlightLines) {
                                        if (lineNum in 1..lineCount) {
                                            drawRect(
                                                color = highlightColor,
                                                topLeft = Offset(0f, padTop + (lineNum - 1) * lineH),
                                                size = Size(size.width, lineH),
                                            )
                                        }
                                    }
                                }
                            }
                            .padding(theme.codeBlockPadding)
                            .onSizeChanged { size ->
                                val currentHeightDp: Dp = with(density) {
                                    (size.height + theme.codeBlockPadding.roundToPx() * 2).toDp()
                                }
                                if (currentHeightDp > stableMinHeight) {
                                    stableMinHeight = currentHeightDp
                                }
                            },
                    )
                }
            }
        } else {
            // plain code (no line numbers, no highlight)
            BasicText(
                text = annotatedString,
                style = theme.codeBlockStyle,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .padding(theme.codeBlockPadding)
                    .onSizeChanged { size ->
                        val currentHeightDp: Dp = with(density) {
                            (size.height + theme.codeBlockPadding.roundToPx() * 2).toDp()
                        }
                        if (currentHeightDp > stableMinHeight) {
                            stableMinHeight = currentHeightDp
                        }
                    },
            )
        }
    }
}

@Composable
private fun CodeBlockTitleBar(title: String, language: String, theme: MarkdownTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.codeBlockTitleBackground)
            .padding(horizontal = theme.codeBlockPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = title,
            style = theme.codeBlockTitleStyle,
        )
    }
}

@Composable
private fun LineNumberGutter(lineCount: Int, theme: MarkdownTheme) {
    val lineNumbers = remember(lineCount) {
        buildAnnotatedString {
            val style = theme.codeBlockStyle.toSpanStyle().copy(
                color = theme.codeBlockLineNumberColor,
            )
            val maxWidth = lineCount.toString().length
            for (i in 1..lineCount) {
                withStyle(style) {
                    append(i.toString().padStart(maxWidth))
                }
                if (i < lineCount) append("\n")
            }
        }
    }
    BasicText(
        text = lineNumbers,
        style = theme.codeBlockStyle,
        softWrap = false,
        modifier = Modifier
            .padding(start = theme.codeBlockPadding, top = theme.codeBlockPadding, bottom = theme.codeBlockPadding)
            .padding(end = 8.dp),
    )
}
