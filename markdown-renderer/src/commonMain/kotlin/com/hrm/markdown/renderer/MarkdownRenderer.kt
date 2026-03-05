package com.hrm.markdown.renderer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.BlankLine
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.renderer.block.BlockRenderer

/**
 * Markdown 渲染器的顶层 Composable 入口。
 *
 * 支持两种使用方式：
 * 1. 传入原始 Markdown 文本，内部自动解析
 * 2. 传入已解析的 AST [Document] 节点
 *
 * 使用 [LazyColumn] 实现高效的长文档滚动渲染。
 *
 * @param markdown 原始 Markdown 文本
 * @param modifier Compose Modifier
 * @param theme 可选的自定义主题
 * @param listState LazyColumn 的滚动状态
 * @param onLinkClick 链接点击回调
 * @param imageRenderer optional custom composable for rendering images.
 *        receives (url, altText, title, modifier).
 *        if null, images are rendered as alt text placeholders.
 */
@Composable
fun Markdown(
    markdown: String,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme(),
    listState: LazyListState = rememberLazyListState(),
    onLinkClick: ((String) -> Unit)? = null,
    imageRenderer: (@Composable (url: String, altText: String, title: String?, modifier: Modifier) -> Unit)? = null,
) {
    val document = remember(markdown) {
        MarkdownParser().parse(markdown)
    }
    InnerMarkdown(
        document = document,
        modifier = modifier,
        theme = theme,
        listState = listState,
        onLinkClick = onLinkClick,
        imageRenderer = imageRenderer,
    )
}

/**
 * 接收已解析的 [Document] 节点进行渲染。
 * 适用于增量解析场景：外部管理 parser 实例并传入更新后的 AST。
 */
@Composable
internal fun InnerMarkdown(
    document: Document,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme(),
    listState: LazyListState = rememberLazyListState(),
    onLinkClick: ((String) -> Unit)? = null,
    imageRenderer: (@Composable (url: String, altText: String, title: String?, modifier: Modifier) -> Unit)? = null,
) {
    val blockNodes = remember(document) {
        document.children.filter { it !is BlankLine }
    }

    ProvideMarkdownTheme(theme) {
        ProvideRendererContext(
            document = document,
            onLinkClick = onLinkClick,
            imageRenderer = imageRenderer,
        ) {
            LazyColumn(
                modifier = modifier,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(theme.blockSpacing),
            ) {
                items(
                    items = blockNodes,
                    key = { it.keyFor() },
                ) { node ->
                    BlockRenderer(node)
                }
            }
        }
    }
}

/**
 * 非 Lazy 版本，用于嵌套在其他可滚动容器中。
 * 例如：BlockQuote、ListItem 内部的子块渲染。
 */
@Composable
internal fun MarkdownBlockChildren(
    parent: ContainerNode,
    modifier: Modifier = Modifier,
) {
    val blockNodes = remember(parent) {
        parent.children.filter { it !is BlankLine }
    }
    val theme = LocalMarkdownTheme.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(theme.blockSpacing),
    ) {
        for (node in blockNodes) {
            BlockRenderer(node)
        }
    }
}

/**
 * 为 LazyColumn 的 item 生成稳定 key。
 * 优先使用 sourceRange（增量解析复用场景下唯一性好），
 * 回退到 hashCode + index 组合。
 */
private fun Node.keyFor(): Any {
    val sr = sourceRange
    return if (sr.start.offset != sr.end.offset) {
        "${sr.start.offset}-${sr.end.offset}"
    } else {
        this.hashCode()
    }
}
