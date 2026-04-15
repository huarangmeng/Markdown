package com.hrm.markdown.renderer.block

import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrm.markdown.parser.ast.DefinitionDescription
import com.hrm.markdown.parser.ast.DefinitionList
import com.hrm.markdown.parser.ast.DefinitionTerm
import com.hrm.markdown.parser.ast.FootnoteDefinition
import com.hrm.markdown.parser.ast.HtmlBlock
import com.hrm.markdown.renderer.LocalFootnoteNavigationState
import com.hrm.markdown.renderer.LocalMarkdownTheme
import com.hrm.markdown.renderer.LocalOnFootnoteBackClick
import com.hrm.markdown.renderer.MarkdownBlockChildren
import com.hrm.markdown.renderer.inline.InlineFlowText
import com.hrm.markdown.renderer.inline.rememberInlineContent

/**
 * HTML 块渲染器：以等宽字体显示原始 HTML。
 */
@Composable
internal fun HtmlBlockRenderer(
    node: HtmlBlock,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMarkdownTheme.current
    Text(
        text = node.literal.trimEnd('\n'),
        modifier = modifier.fillMaxWidth(),
        style = theme.codeBlockStyle.copy(fontFamily = FontFamily.Monospace),
    )
}

/**
 * 定义列表渲染器。
 */
@Composable
internal fun DefinitionListRenderer(
    node: DefinitionList,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMarkdownTheme.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (child in node.children) {
            when (child) {
                is DefinitionTerm -> {
                    val inlineResult = rememberInlineContent(
                        parent = child,
                        hostTextStyle = theme.bodyStyle.copy(fontWeight = FontWeight.Bold),
                    )
                    InlineFlowText(
                        annotated = inlineResult.annotated,
                        inlineContents = inlineResult.inlineContents,
                        style = theme.bodyStyle.copy(fontWeight = FontWeight.Bold),
                    )
                }
                is DefinitionDescription -> {
                    MarkdownBlockChildren(
                        parent = child,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
                else -> BlockRenderer(child)
            }
        }
    }
}

/**
 * 脚注定义渲染器。
 */
@Composable
internal fun FootnoteDefinitionRenderer(
    node: FootnoteDefinition,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMarkdownTheme.current
    val footnoteNavigationState = LocalFootnoteNavigationState.current
    val onFootnoteBackClick = LocalOnFootnoteBackClick.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val canReturn = footnoteNavigationState?.hasReturnPosition(node.label) == true

    DisposableEffect(footnoteNavigationState, node.label, bringIntoViewRequester) {
        footnoteNavigationState?.registerDefinition(node.label, bringIntoViewRequester)
        onDispose {
            footnoteNavigationState?.unregisterDefinition(node.label, bringIntoViewRequester)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "[${node.index}] ${node.label}",
                style = theme.bodyStyle.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = theme.footnoteStyle.fontSize,
                ),
            )
            if (canReturn && onFootnoteBackClick != null) {
                Text(
                    text = "↩ 返回",
                    modifier = Modifier.clickable { onFootnoteBackClick(node.label) },
                    style = theme.bodyStyle.copy(
                        color = theme.linkColor,
                        fontSize = theme.footnoteStyle.fontSize,
                    ),
                )
            }
        }
        MarkdownBlockChildren(
            parent = node,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
