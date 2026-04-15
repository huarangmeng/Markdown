package com.hrm.markdown.renderer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.hrm.codehigh.theme.CodeTheme
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.BlankLine
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.log.HLog
import com.hrm.markdown.renderer.block.BlockRenderer
import com.hrm.markdown.renderer.block.blockRenderRevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG_RENDER = "MarkdownRender"

/**
 * Markdown 渲染器的顶层 Composable 入口。
 *
 * 自动异步解析 Markdown 文本并渲染。
 *
 * 普通用法：
 * ```
 * Markdown(markdown = "# Hello World")
 * ```
 *
 * 流式用法（LLM 逐 token 输出场景）：
 * ```
 * var text by remember { mutableStateOf("") }
 * var isStreaming by remember { mutableStateOf(true) }
 *
 * LaunchedEffect(Unit) {
 *     tokens.collect { token ->
 *         text += token
 *     }
 *     isStreaming = false
 * }
 *
 * Markdown(markdown = text, isStreaming = isStreaming)
 * ```
 *
 * @param markdown 原始 Markdown 文本
 * @param isStreaming 是否处于流式生成中。为 true 时启用增量解析，避免全量重解析导致的闪烁
 * @param theme 可选的自定义主题，默认跟随系统日夜间模式
 * @param config 解析配置，控制 Markdown 方言（Flavour）和解析行为，默认使用 [MarkdownConfig.Default]（ExtendedFlavour 全功能）
 * @param scrollState 滚动状态，外部可控制滚动位置
 * @param retainStateOnChange 当 markdown 变化时是否保留旧内容直到新内容解析完成（避免闪烁）
 * @param enablePagination 是否启用分页加载，适合超长文档（> 500 段落）
 * @param initialBlockCount 分页模式下初始渲染的块数量
 * @param imageContent 自定义图片渲染组件，null 则使用默认占位渲染
 * @param onLinkClick 链接点击回调
 */
@Composable
fun Markdown(
    markdown: String,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme.auto(),
    codeTheme: CodeTheme? = null,
    config: MarkdownConfig = MarkdownConfig.Default,
    scrollState: ScrollState = rememberScrollState(),
    isStreaming: Boolean = false,
    retainStateOnChange: Boolean = false,
    enablePagination: Boolean = false,
    enableScroll: Boolean = true,
    initialBlockCount: Int = 100,
    imageContent: MarkdownImageRenderer? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val document = rememberStreamingDocument(markdown, isStreaming, config)

    if (document == null) {
        if (isStreaming) return
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        InnerMarkdown(
            document = document,
            modifier = modifier,
            theme = theme,
            codeTheme = codeTheme,
            config = config,
            scrollState = scrollState,
            isStreaming = isStreaming,
            enablePagination = enablePagination,
            enableScroll = enableScroll,
            initialBlockCount = initialBlockCount,
            imageContent = imageContent,
            onLinkClick = onLinkClick,
        )
    }
}

/**
 * Markdown 渲染器的顶层 Composable 入口，支持传入自定义 AST，供需要高度定制的场景使用。
 *
 * @param document Markdown AST
 * @param isStreaming 是否处于流式生成中。为 true 时启用增量解析，避免全量重解析导致的闪烁
 * @param theme 可选的自定义主题，默认跟随系统日夜间模式
 * @param config 解析配置，控制 Markdown 方言（Flavour）和解析行为，默认使用 [MarkdownConfig.Default]（ExtendedFlavour 全功能）
 * @param scrollState 滚动状态，外部可控制滚动位置
 * @param retainStateOnChange 当 markdown 变化时是否保留旧内容直到新内容解析完成（避免闪烁）
 * @param enablePagination 是否启用分页加载，适合超长文档（> 500 段落）
 * @param initialBlockCount 分页模式下初始渲染的块数量
 * @param imageContent 自定义图片渲染组件，null 则使用默认占位渲染
 * @param onLinkClick 链接点击回调
 */
@Composable
fun Markdown(
    document: Document,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme.auto(),
    codeTheme: CodeTheme? = null,
    config: MarkdownConfig = MarkdownConfig.Default,
    scrollState: ScrollState = rememberScrollState(),
    isStreaming: Boolean = false,
    retainStateOnChange: Boolean = false,
    enablePagination: Boolean = false,
    enableScroll: Boolean = true,
    initialBlockCount: Int = 100,
    imageContent: MarkdownImageRenderer? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    InnerMarkdown(
        document = document,
        modifier = modifier,
        theme = theme,
        codeTheme = codeTheme,
        config = config,
        scrollState = scrollState,
        isStreaming = isStreaming,
        enablePagination = enablePagination,
        enableScroll = enableScroll,
        initialBlockCount = initialBlockCount,
        imageContent = imageContent,
        onLinkClick = onLinkClick,
    )
}

/**
 * 流式文档解析状态。
 * - 流式模式：增量追加解析，避免闪烁
 * - 非流式模式：全量异步解析
 */
@Composable
private fun rememberStreamingDocument(
    markdown: String,
    isStreaming: Boolean,
    config: MarkdownConfig = MarkdownConfig.Default,
): Document? {
    val parser = remember(config) {
        MarkdownParser(
            flavour = config.flavour,
            customEmojiMap = config.customEmojiMap,
            enableAsciiEmoticons = config.enableAsciiEmoticons,
            enableLinting = config.enableLinting,
        )
    }
    var state by remember(parser) { mutableStateOf(StreamingDocumentState<Document>()) }

    LaunchedEffect(markdown, isStreaming, parser) {
        state = updateStreamingDocumentState(
            markdown = markdown,
            isStreaming = isStreaming,
            state = state,
            beginStream = parser::beginStream,
            append = parser::append,
            endStream = parser::endStream,
            parse = { value ->
                withContext(Dispatchers.Default) {
                    parser.parse(value)
                }
            }
        )
    }

    return state.document
}

internal data class StreamingDocumentState<T>(
    val lastParsedLength: Int = 0,
    val document: T? = null,
    val wasStreaming: Boolean = false,
    val lastNonStreamingMarkdown: String = "",
)

internal suspend fun <T> updateStreamingDocumentState(
    markdown: String,
    isStreaming: Boolean,
    state: StreamingDocumentState<T>,
    beginStream: () -> Unit,
    append: (String) -> T,
    endStream: () -> T,
    parse: suspend (String) -> T?,
): StreamingDocumentState<T> {
    var nextState = state

    if (isStreaming && !nextState.wasStreaming) {
        beginStream()
        nextState = nextState.copy(
            lastParsedLength = 0,
            document = null,
            wasStreaming = true,
        )
    }

    if (isStreaming) {
        if (markdown.length > nextState.lastParsedLength) {
            val chunk = markdown.substring(nextState.lastParsedLength)
            if (chunk.isNotEmpty()) {
                nextState = nextState.copy(
                    document = append(chunk),
                    lastParsedLength = markdown.length,
                )
            }
        }
        return nextState.copy(wasStreaming = true)
    }

    if (nextState.wasStreaming) {
        if (markdown.length > nextState.lastParsedLength) {
            val chunk = markdown.substring(nextState.lastParsedLength)
            if (chunk.isNotEmpty()) {
                nextState = nextState.copy(
                    document = append(chunk),
                    lastParsedLength = markdown.length,
                )
            }
        }
        return nextState.copy(
            document = endStream(),
            lastParsedLength = markdown.length,
            wasStreaming = false,
            lastNonStreamingMarkdown = markdown,
        )
    }

    if (markdown == nextState.lastNonStreamingMarkdown) {
        return nextState.copy(wasStreaming = false)
    }

    return nextState.copy(
        document = parse(markdown),
        lastParsedLength = markdown.length,
        wasStreaming = false,
        lastNonStreamingMarkdown = markdown,
    )
}

@Composable
private fun InnerMarkdown(
    document: Document,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme.auto(),
    codeTheme: CodeTheme? = null,
    config: MarkdownConfig = MarkdownConfig.Default,
    scrollState: ScrollState = rememberScrollState(),
    isStreaming: Boolean = false,
    enablePagination: Boolean = false,
    enableScroll: Boolean = true,
    initialBlockCount: Int = 100,
    imageContent: MarkdownImageRenderer? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val latestDocument by rememberUpdatedState(document)
    var throttledDocument by remember { mutableStateOf(document, neverEqualPolicy()) }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            throttledDocument = latestDocument
            return@LaunchedEffect
        }

        while (true) {
            withFrameNanos { }
            delay(16L)
            throttledDocument = latestDocument
        }
    }

    // 使用节流后的 document 进行渲染
    val renderDocument = throttledDocument

    // 使用结构性比较缓存 blockNodes：
    // 每次 token 到达都产生新的 Document 对象，但大部分 children 的引用没变。
    // 流式期间仅在块结构变化，或同一块的 renderRevision 变化时才刷新状态，
    // 避免每个 token 都强制推整列 blockNodes。
    val blockNodesState = remember { mutableStateOf(emptyList<Node>(), neverEqualPolicy()) }
    val blockNodeRevisionsState = remember { mutableStateOf(emptyList<Long>()) }
    val newChildren = renderDocument.children
    val newFiltered = newChildren.filter { it !is BlankLine }
    val currentList = blockNodesState.value
    val newRevisions = newFiltered.map(::blockRenderRevision)
    val shouldRefreshBlockNodes = !structurallyEqual(currentList, newFiltered) ||
            !revisionsEqual(blockNodeRevisionsState.value, newRevisions)
    if (shouldRefreshBlockNodes) {
        HLog.d(TAG_RENDER) { "blockNodes updated: ${currentList.size} -> ${newFiltered.size}" }
        blockNodesState.value = newFiltered.toList()
        blockNodeRevisionsState.value = newRevisions
    }

    val blockNodes = blockNodesState.value
    val paginationStateKey = if (enablePagination && !isStreaming) document else Unit
    // P1: 分页加载支持 - 渐进式渲染超长文档
    var visibleBlockCount by remember(paginationStateKey, initialBlockCount) {
        mutableIntStateOf(initialVisibleBlockCount(initialBlockCount, blockNodes.size))
    }
    val effectiveVisibleBlockCount = visibleBlockCount.coerceAtMost(blockNodes.size)

    // 监听滚动位置，接近底部时自动加载更多块
    LaunchedEffect(scrollState, enablePagination, blockNodes.size) {
        if (!enablePagination) return@LaunchedEffect

        snapshotFlow {
            if (scrollState.maxValue > 0) {
                scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            } else {
                0f
            }
        }.collect { scrollProgress ->
            // 滚动到 80% 时加载下一批
            if (scrollProgress > 0.8f && visibleBlockCount < blockNodes.size) {
                val increment = 50 // 每次加载 50 个块
                visibleBlockCount = (visibleBlockCount + increment).coerceAtMost(blockNodes.size)
            }
        }
    }

    // 计算实际渲染的块列表
    // 注意：直接读取 blockNodesState.value 而非局部变量，确保 derivedStateOf 能追踪状态变化
    val renderBlocks by remember {
        derivedStateOf {
            val nodes = blockNodesState.value
            if (enablePagination) {
                nodes.take(effectiveVisibleBlockCount)
            } else {
                nodes
            }
        }
    }

    val footnoteNavigationState = remember { FootnoteNavigationState() }
    val coroutineScope = rememberCoroutineScope()
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    val currentBlockCount = rememberUpdatedState(blockNodes.size)
    val onFootnoteClick = remember(footnoteNavigationState, enablePagination) {
        { label: String ->
            coroutineScope.launch {
                footnoteNavigationState.rememberReturnPosition(label, scrollState.value)

                if (enablePagination && !footnoteNavigationState.hasDefinition(label)) {
                    visibleBlockCount = currentBlockCount.value
                    withFrameNanos { }
                }

                if (!footnoteNavigationState.bringDefinitionIntoView(label)) {
                    currentOnLinkClick.value?.invoke("#fn-$label")
                }
            }
            Unit
        }
    }
    val onFootnoteBackClick = remember(footnoteNavigationState) {
        { label: String ->
            coroutineScope.launch {
                val returnPosition = footnoteNavigationState.getReturnPosition(label)
                if (returnPosition != null && enableScroll) {
                    scrollState.animateScrollTo(returnPosition)
                    return@launch
                }

                footnoteNavigationState.bringReferenceIntoView(label)
            }
            Unit
        }
    }

    ProvideMarkdownTheme(theme) {
        ProvideRendererContext(
            document = renderDocument,
            onLinkClick = onLinkClick,
            onFootnoteClick = onFootnoteClick,
            onFootnoteBackClick = onFootnoteBackClick,
            footnoteNavigationState = footnoteNavigationState,
            imageContent = imageContent,
            config = config,
            codeTheme = codeTheme,
            isStreaming = isStreaming,
        ) {
            // 流式生成期间跳过 SelectionContainer：
            // SelectionContainer 在内容高频变化时会对内部布局做额外的 intrinsic 测量
            // （用于计算选择手柄位置），叠加代码块等长内容的重组，加重布局抖动。
            // 流式结束后恢复 SelectionContainer，用户可以正常选择文本。
            val content: @Composable () -> Unit = {
                Column(
                    modifier = modifier
                        .then(if (enableScroll) Modifier.verticalScroll(scrollState) else Modifier)
                        .graphicsLayer { },
                    verticalArrangement = Arrangement.spacedBy(theme.blockSpacing),
                ) {
                    for (node in renderBlocks) {
                        key(node::class, node.stableKey) {
                            BlockRenderer(
                                node = node,
                                renderRevision = blockRenderRevision(node),
                            )
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
            key(node::class, node.stableKey) {
                BlockRenderer(
                    node = node,
                    renderRevision = blockRenderRevision(node),
                )
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

private fun revisionsEqual(a: List<Long>, b: List<Long>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] != b[i]) return false
    }
    return true
}

private fun initialVisibleBlockCount(initialBlockCount: Int, totalBlockCount: Int): Int {
    return initialBlockCount.coerceAtLeast(0).coerceAtMost(totalBlockCount.coerceAtLeast(0))
}
