package com.hrm.markdown.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.runtime.MarkdownDirectivePipeline
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 流式文档解析状态。
 * - 流式模式：增量追加解析，避免闪烁
 * - 非流式模式：全量异步解析
 */
@Composable
internal fun rememberStreamingDocument(
    markdown: String,
    isStreaming: Boolean,
    config: MarkdownConfig = MarkdownConfig.Default,
    runtimePipeline: MarkdownDirectivePipeline = MarkdownDirectivePipeline(MarkdownDirectiveRegistry.Empty),
): Document? {
    val parser = remember(config) {
        MarkdownParser(
            flavour = config.flavour,
            customEmojiMap = config.customEmojiMap,
            enableAsciiEmoticons = config.enableAsciiEmoticons,
            enableLinting = config.enableLinting,
            appendCoalesceThreshold = config.appendCoalesceThreshold,
        )
    }
    var state by remember(parser, runtimePipeline) { mutableStateOf(StreamingDocumentState<Document>()) }

    LaunchedEffect(markdown, isStreaming, parser, runtimePipeline) {
        state = updateStreamingDocumentState(
            markdown = markdown,
            isStreaming = isStreaming,
            state = state,
            beginStream = parser::beginStream,
            append = parser::append,
            endStream = parser::endStream,
            parse = { value ->
                withContext(Dispatchers.Default) {
                    parser.parse(runtimePipeline.transform(value).markdown)
                }
            }
        )
    }

    return state.document
}

internal data class StreamingDocumentState<T>(
    /** 已提交给流式解析器的原始 Markdown，用于验证下一次更新确实是 append-only。 */
    val streamedMarkdown: String = "",
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
            streamedMarkdown = "",
            document = null,
            wasStreaming = true,
        )
    }

    if (isStreaming) {
        if (!markdown.startsWith(nextState.streamedMarkdown)) {
            // retry / clear / replace 不是 append-only；原会话已经不可复用，原子地重启。
            beginStream()
            nextState = nextState.copy(
                streamedMarkdown = "",
                document = null,
                wasStreaming = true,
            )
        }
        val chunk = markdown.substring(nextState.streamedMarkdown.length)
        if (chunk.isNotEmpty()) {
            nextState = nextState.copy(document = append(chunk))
        }
        return nextState.copy(
            streamedMarkdown = markdown,
            wasStreaming = true,
        )
    }

    if (nextState.wasStreaming) {
        if (!markdown.startsWith(nextState.streamedMarkdown)) {
            // 流结束时上游可能已经用重试结果替换全文；直接全量解析该最终真相。
            return nextState.copy(
                streamedMarkdown = "",
                document = parse(markdown),
                wasStreaming = false,
                lastNonStreamingMarkdown = markdown,
            )
        }
        val chunk = markdown.substring(nextState.streamedMarkdown.length)
        if (chunk.isNotEmpty()) {
            nextState = nextState.copy(document = append(chunk))
        }
        return nextState.copy(
            streamedMarkdown = "",
            document = endStream(),
            wasStreaming = false,
            lastNonStreamingMarkdown = markdown,
        )
    }

    if (markdown == nextState.lastNonStreamingMarkdown) {
        return nextState.copy(wasStreaming = false)
    }

    return nextState.copy(
        streamedMarkdown = "",
        document = parse(markdown),
        wasStreaming = false,
        lastNonStreamingMarkdown = markdown,
    )
}
