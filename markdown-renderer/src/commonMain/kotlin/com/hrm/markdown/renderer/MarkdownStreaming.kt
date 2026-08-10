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
import com.hrm.markdown.runtime.MarkdownSourceMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
): StreamingMarkdownSnapshot? {
    val parser = remember(config) { config.newParser() }
    var snapshotState by remember(parser, runtimePipeline) {
        mutableStateOf(StreamingMarkdownState())
    }
    val transformMutex = remember(runtimePipeline) { Mutex() }

    LaunchedEffect(markdown, isStreaming, parser, runtimePipeline) {
        val transformed = if (runtimePipeline.hasTransformers) {
            withContext(Dispatchers.Default) {
                transformMutex.withLock { runtimePipeline.transform(markdown) }
            }
        } else {
            com.hrm.markdown.runtime.MarkdownTransformResult(markdown)
        }
        val documentState = updateStreamingDocumentState(
            markdown = transformed.markdown,
            isStreaming = isStreaming,
            state = snapshotState.documentState,
            beginStream = parser::beginStream,
            append = parser::append,
            endStream = parser::endStream,
            parse = { value ->
                withContext(Dispatchers.Default) {
                    // Full parses own a disposable parser. Cancellation can leave a synchronous
                    // parse running, but it can no longer race with the stateful streaming parser.
                    config.newParser().parse(value)
                }
            }
        )
        // Document and source map describe one transformed input revision and must become visible
        // atomically; publishing them through separate Compose states can briefly pair revisions.
        snapshotState = StreamingMarkdownState(documentState, transformed.sourceMap)
    }

    return snapshotState.documentState.document?.let { document ->
        StreamingMarkdownSnapshot(document, snapshotState.sourceMap)
    }
}

private data class StreamingMarkdownState(
    val documentState: StreamingDocumentState<Document> = StreamingDocumentState(),
    val sourceMap: MarkdownSourceMap = MarkdownSourceMap.Identity,
)

internal data class StreamingMarkdownSnapshot(
    val document: Document,
    val sourceMap: MarkdownSourceMap,
)

private fun MarkdownConfig.newParser(): MarkdownParser = MarkdownParser(
    flavour = flavour,
    customEmojiMap = customEmojiMap,
    enableAsciiEmoticons = enableAsciiEmoticons,
    enableLinting = enableLinting,
    appendCoalesceThreshold = appendCoalesceThreshold,
)

internal data class StreamingDocumentState<T>(
    /** 已提交给流式解析器的原始 Markdown，用于验证下一次更新确实是 append-only。 */
    val streamedMarkdown: String = "",
    val document: T? = null,
    val wasStreaming: Boolean = false,
    /** Null means no non-streaming revision has been parsed yet; empty Markdown is still a revision. */
    val lastNonStreamingMarkdown: String? = null,
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
