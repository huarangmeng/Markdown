package com.hrm.markdown.renderer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.BlankLine
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.renderer.block.BlockRenderer
import kotlinx.coroutines.delay

/**
 * Markdown 渲染器的顶层 Composable 入口。
 *
 * @param markdown 原始 Markdown 文本
 * @param modifier Compose Modifier
 * @param theme 可选的自定义主题
 * @param scrollState 滚动状态，外部可控制滚动位置
 * @param imageContent 自定义图片渲染组件，null 则使用默认占位渲染
 * @param onLinkClick 链接点击回调
 */
@Composable
fun Markdown(
    markdown: String,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme(),
    scrollState: ScrollState = rememberScrollState(),
    imageContent: MarkdownImageRenderer? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val document = remember(markdown) {
        MarkdownParser().parse(markdown)
    }
    InnerMarkdown(
        document = document,
        modifier = modifier,
        theme = theme,
        scrollState = scrollState,
        imageContent = imageContent,
        onLinkClick = onLinkClick,
    )
}

/**
 * 接收已解析的 [Document] 节点进行渲染。
 * 适用于流式/增量解析场景：外部管理 MarkdownParser 实例并传入更新后的 AST。
 *
 * 典型用法：
 * ```
 * val parser = remember { MarkdownParser() }
 * var doc by remember { mutableStateOf(parser.parse("")) }
 * LaunchedEffect(Unit) {
 *     parser.beginStream()
 *     tokens.collect { chunk ->
 *         doc = parser.append(chunk)
 *     }
 *     doc = parser.endStream()
 * }
 * Markdown(document = doc, isStreaming = parser.isStreaming, ...)
 * ```
 *
 * @param isStreaming 是否处于流式生成中。为 true 时跳过 [SelectionContainer] 包裹，
 *   避免 SelectionContainer 在高频内容变化时对内部布局做额外的 intrinsic 测量导致抖动；
 *   流式结束后设为 false，自动恢复文本选择能力。
 * @param imageContent 自定义图片渲染组件，null 则使用默认占位渲染
 */
@Composable
fun Markdown(
    document: Document,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme(),
    scrollState: ScrollState = rememberScrollState(),
    isStreaming: Boolean = false,
    imageContent: MarkdownImageRenderer? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    InnerMarkdown(
        document = document,
        modifier = modifier,
        theme = theme,
        scrollState = scrollState,
        isStreaming = isStreaming,
        imageContent = imageContent,
        onLinkClick = onLinkClick,
    )
}

@Composable
internal fun InnerMarkdown(
    document: Document,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme(),
    scrollState: ScrollState = rememberScrollState(),
    isStreaming: Boolean = false,
    imageContent: MarkdownImageRenderer? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    // ═══ 流式节流：200ms 采样一次，减少高频重组 ═══
    // 流式期间（isStreaming=true）：每 200ms 采样一次最新的 document 进行渲染，
    // 上游解析不受影响（每个 token 仍然 append），但渲染层降频到 ~5fps。
    // 流式结束时（isStreaming 变为 false）：立即消费最终的 document。
    val latestDocument by rememberUpdatedState(document)
    var throttledDocument by remember { mutableStateOf(document) }

    if (!isStreaming) {
        // 非流式模式：直接使用最新 document，无节流
        throttledDocument = document
    }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) return@LaunchedEffect
        // 流式期间：每 200ms 将最新的 document 同步到 throttledDocument
        while (true) {
            delay(200L)
            throttledDocument = latestDocument
        }
    }

    // 使用节流后的 document 进行渲染
    val renderDocument = throttledDocument

    // 使用结构性比较缓存 blockNodes：
    // 每次 token 到达都产生新的 Document 对象，但大部分 children 的引用没变。
    // 通过比较 children 列表的引用身份（size + 首尾元素引用 + stableKey 序列），
    // 只在结构真正变化时才更新 blockNodes 状态，避免不必要的 Column 重组。
    val blockNodesState = remember { mutableStateOf(emptyList<Node>()) }
    val newChildren = renderDocument.children
    val newFiltered = newChildren.filter { it !is BlankLine }
    val currentList = blockNodesState.value
    if (!structurallyEqual(currentList, newFiltered)) {
        blockNodesState.value = newFiltered
    }
    val blockNodes = blockNodesState.value

    ProvideMarkdownTheme(theme) {
        ProvideRendererContext(
            document = renderDocument,
            onLinkClick = onLinkClick,
            imageContent = imageContent,
        ) {
            // 流式生成期间跳过 SelectionContainer：
            // SelectionContainer 在内容高频变化时会对内部布局做额外的 intrinsic 测量
            // （用于计算选择手柄位置），叠加代码块等长内容的重组，加重布局抖动。
            // 流式结束后恢复 SelectionContainer，用户可以正常选择文本。
            val content: @Composable () -> Unit = {
                Column(
                    modifier = modifier
                        .verticalScroll(scrollState)
                        // graphicsLayer 创建独立绘制层，将 Column 的绘制与外层隔离。
                        // 这样滚动位置变化只在此层处理，不会触发外层的绘制失效。
                        .graphicsLayer { },
                    verticalArrangement = Arrangement.spacedBy(theme.blockSpacing),
                ) {
                    for (node in blockNodes) {
                        key(node.stableKey) {
                            BlockRenderer(node)
                        }
                    }
                }
            }

            if (isStreaming) {
                content()
            } else {
                SelectionContainer {
                    content()
                }
            }
        }
    }
}

/**
 * 非 Lazy 版本，用于嵌套在其他容器中。
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
            key(node.stableKey) {
                BlockRenderer(node)
            }
        }
    }
}

/**
 * 结构性比较两个节点列表：
 * - 长度相同
 * - 每个位置的节点引用相同（=== 引用比较）
 *
 * 这比 `remember(document)` 更精确：当 Document 对象每次都是新的，
 * 但 children 列表的结构（对象引用）没变时，返回 true → 避免不必要的重组。
 */
private fun structurallyEqual(a: List<Node>, b: List<Node>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] !== b[i]) return false
    }
    return true
}
