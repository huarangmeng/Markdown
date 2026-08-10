package com.hrm.markdown.renderer

import com.hrm.markdown.runtime.MarkdownDirectivePipeline
import com.hrm.markdown.runtime.MarkdownDirectivePlugin
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import com.hrm.markdown.runtime.MarkdownInputTransformer
import com.hrm.markdown.runtime.MarkdownTransformResult
import com.hrm.markdown.runtime.MarkdownTransformerStreamingSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamingDocumentStateTest {

    @Test
    fun should_restartStream_when_defaultTransformerRewritesPreviousOutput() = runBlocking {
        val transformer = object : MarkdownInputTransformer {
            override val id: String = "default-rewrite"

            override fun transform(input: String): MarkdownTransformResult =
                MarkdownTransformResult(
                    if (input.endsWith("}")) "{% normalized %}" else input
                )
        }
        val plugin = object : MarkdownDirectivePlugin {
            override val id: String = "default-rewrite"
            override val inputTransformers: List<MarkdownInputTransformer> = listOf(transformer)
        }
        val pipeline = MarkdownDirectivePipeline(MarkdownDirectiveRegistry(listOf(plugin)))
        val calls = mutableListOf<String>()
        val partial = pipeline.transform("custom{").markdown
        val initial = updateStreamingDocumentState(
            markdown = partial,
            isStreaming = true,
            state = StreamingDocumentState(),
            beginStream = { calls += "begin" },
            append = { chunk ->
                calls += "append:$chunk"
                chunk
            },
            endStream = { "end" },
            parse = { it },
        )
        val completed = pipeline.transform("custom{}").markdown
        val restarted = updateStreamingDocumentState(
            markdown = completed,
            isStreaming = true,
            state = initial,
            beginStream = { calls += "begin" },
            append = { chunk ->
                calls += "append:$chunk"
                chunk
            },
            endStream = { "end" },
            parse = { it },
        )

        assertEquals(
            MarkdownTransformerStreamingSupport.RestartOnRewrite,
            pipeline.streamingSupport,
        )
        assertTrue(pipeline.supportsStreaming)
        assertEquals(
            listOf("begin", "append:custom{", "begin", "append:{% normalized %}"),
            calls,
        )
        assertEquals("{% normalized %}", restarted.streamedMarkdown)
        assertTrue(restarted.wasStreaming)
    }

    @Test
    fun should_parse_initial_empty_non_streaming_document() = runBlocking {
        var parseCount = 0

        val state = updateStreamingDocumentState(
            markdown = "",
            isStreaming = false,
            state = StreamingDocumentState(),
            beginStream = {},
            append = { it },
            endStream = { "end" },
            parse = {
                parseCount++
                "empty document"
            },
        )

        assertEquals(1, parseCount)
        assertEquals("empty document", state.document)
        assertEquals("", state.lastNonStreamingMarkdown)
    }

    @Test
    fun should_begin_before_append_when_stream_starts() = runBlocking {
        val calls = mutableListOf<String>()

        val state = updateStreamingDocumentState(
            markdown = "abcd",
            isStreaming = true,
            state = StreamingDocumentState(),
            beginStream = {
                calls += "begin"
            },
            append = { chunk ->
                calls += "append:$chunk"
                "append:$chunk"
            },
            endStream = {
                calls += "end"
                "end"
            },
            parse = { markdown ->
                calls += "parse:$markdown"
                "parse:$markdown"
            }
        )

        assertEquals(listOf("begin", "append:abcd"), calls)
        assertEquals("append:abcd", state.document)
        assertEquals("abcd", state.streamedMarkdown)
        assertTrue(state.wasStreaming)
    }

    @Test
    fun should_append_tail_before_end_when_stream_finishes() = runBlocking {
        val calls = mutableListOf<String>()

        val state = updateStreamingDocumentState(
            markdown = "abcd",
            isStreaming = false,
            state = StreamingDocumentState(
                streamedMarkdown = "abc",
                document = "append:abc",
                wasStreaming = true,
            ),
            beginStream = {
                calls += "begin"
            },
            append = { chunk ->
                calls += "append:$chunk"
                "append:$chunk"
            },
            endStream = {
                calls += "end"
                "end"
            },
            parse = { markdown ->
                calls += "parse:$markdown"
                "parse:$markdown"
            }
        )

        assertEquals(listOf("append:d", "end"), calls)
        assertEquals("end", state.document)
        assertEquals("", state.streamedMarkdown)
        assertFalse(state.wasStreaming)
        assertEquals("abcd", state.lastNonStreamingMarkdown)
    }

    @Test
    fun should_not_parse_when_finishing_stream() = runBlocking {
        val calls = mutableListOf<String>()

        updateStreamingDocumentState(
            markdown = "abc",
            isStreaming = false,
            state = StreamingDocumentState(
                streamedMarkdown = "abc",
                document = "append:abc",
                wasStreaming = true,
            ),
            beginStream = {
                calls += "begin"
            },
            append = { chunk ->
                calls += "append:$chunk"
                "append:$chunk"
            },
            endStream = {
                calls += "end"
                "end"
            },
            parse = { markdown ->
                calls += "parse:$markdown"
                "parse:$markdown"
            }
        )

        assertEquals(listOf("end"), calls)
    }

    @Test
    fun should_parse_full_markdown_when_not_streaming() = runBlocking {
        val calls = mutableListOf<String>()

        val state = updateStreamingDocumentState(
            markdown = "abc",
            isStreaming = false,
            state = StreamingDocumentState(),
            beginStream = {
                calls += "begin"
            },
            append = { chunk ->
                calls += "append:$chunk"
                "append:$chunk"
            },
            endStream = {
                calls += "end"
                "end"
            },
            parse = { markdown ->
                calls += "parse:$markdown"
                "parse:$markdown"
            }
        )

        assertEquals(listOf("parse:abc"), calls)
        assertEquals("parse:abc", state.document)
        assertEquals("", state.streamedMarkdown)
        assertFalse(state.wasStreaming)
        assertEquals("abc", state.lastNonStreamingMarkdown)
    }

    @Test
    fun should_restart_stream_when_markdown_is_replaced() = runBlocking {
        val calls = mutableListOf<String>()

        val state = updateStreamingDocumentState(
            markdown = "new",
            isStreaming = true,
            state = StreamingDocumentState(
                streamedMarkdown = "old content",
                document = "append:old content",
                wasStreaming = true,
            ),
            beginStream = { calls += "begin" },
            append = { chunk ->
                calls += "append:$chunk"
                "append:$chunk"
            },
            endStream = {
                calls += "end"
                "end"
            },
            parse = { value ->
                calls += "parse:$value"
                "parse:$value"
            },
        )

        assertEquals(listOf("begin", "append:new"), calls)
        assertEquals("append:new", state.document)
        assertEquals("new", state.streamedMarkdown)
        assertTrue(state.wasStreaming)
    }

    @Test
    fun should_parse_replaced_final_markdown_instead_of_ending_old_stream() = runBlocking {
        val calls = mutableListOf<String>()

        val state = updateStreamingDocumentState(
            markdown = "replacement",
            isStreaming = false,
            state = StreamingDocumentState(
                streamedMarkdown = "old content",
                document = "append:old content",
                wasStreaming = true,
            ),
            beginStream = { calls += "begin" },
            append = { chunk ->
                calls += "append:$chunk"
                "append:$chunk"
            },
            endStream = {
                calls += "end"
                "end"
            },
            parse = { value ->
                calls += "parse:$value"
                "parse:$value"
            },
        )

        assertEquals(listOf("parse:replacement"), calls)
        assertEquals("parse:replacement", state.document)
        assertEquals("replacement", state.lastNonStreamingMarkdown)
        assertFalse(state.wasStreaming)
    }
}
