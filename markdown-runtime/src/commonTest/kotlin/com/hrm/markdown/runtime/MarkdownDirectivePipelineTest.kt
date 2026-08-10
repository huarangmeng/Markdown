package com.hrm.markdown.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarkdownDirectivePipelineTest {
    @Test
    fun transformer_should_normalize_custom_syntax() {
        val registry = MarkdownDirectiveRegistry(listOf(VideoDirectivePlugin))
        val result = MarkdownDirectivePipeline(registry).transform(
            "!VIDEO[Demo](https://cdn.example.com/a.mp4){poster=https://cdn.example.com/a.jpg}"
        )

        assertEquals(
            """{% video title="Demo" url="https://cdn.example.com/a.mp4" poster="https://cdn.example.com/a.jpg" %}""",
            result.markdown,
        )
    }

    @Test
    fun later_plugin_should_override_same_tag_renderer() {
        val first = RecordingDirectivePlugin("first")
        val second = RecordingDirectivePlugin("second", priority = 1)
        val registry = MarkdownDirectiveRegistry(listOf(second, first))

        assertSame(second.renderer, registry.findBlockDirectiveRenderer("video"))
    }

    @Test
    fun later_plugin_should_override_same_inline_renderer() {
        val first = RecordingDirectivePlugin("first")
        val second = RecordingDirectivePlugin("second", priority = 1)
        val registry = MarkdownDirectiveRegistry(listOf(second, first))

        assertSame(second.inlineRenderer, registry.findInlineDirectiveRenderer("badge"))
    }

    @Test
    fun registry_should_select_strongestSafePipelineStreamingMode() {
        val restartOnRewrite = MarkdownDirectiveRegistry(listOf(VideoDirectivePlugin))
        val safe = MarkdownDirectiveRegistry(
            listOf(
                object : MarkdownDirectivePlugin {
                    override val id: String = "append-safe"
                    override val inputTransformers = listOf(
                        object : MarkdownInputTransformer {
                            override val id: String = "identity"
                            override val streamingSupport =
                                MarkdownTransformerStreamingSupport.AppendSafe

                            override fun transform(input: String) = MarkdownTransformResult(input)
                        }
                    )
                }
            )
        )
        val unsupported = MarkdownDirectiveRegistry(
            listOf(
                object : MarkdownDirectivePlugin {
                    override val id: String = "unsupported"
                    override val inputTransformers = listOf(
                        object : MarkdownInputTransformer {
                            override val id: String = "unsupported"
                            override val streamingSupport =
                                MarkdownTransformerStreamingSupport.Unsupported

                            override fun transform(input: String) = MarkdownTransformResult(input)
                        }
                    )
                }
            )
        )

        assertEquals(
            MarkdownTransformerStreamingSupport.RestartOnRewrite,
            restartOnRewrite.streamingSupport,
        )
        assertTrue(restartOnRewrite.supportsStreaming)
        assertFalse(restartOnRewrite.supportsStreamingFastPath)
        assertEquals(MarkdownTransformerStreamingSupport.AppendSafe, safe.streamingSupport)
        assertTrue(safe.supportsStreaming)
        assertTrue(safe.supportsStreamingFastPath)
        assertEquals(MarkdownTransformerStreamingSupport.Unsupported, unsupported.streamingSupport)
        assertFalse(unsupported.supportsStreaming)
        assertFalse(unsupported.supportsStreamingFastPath)
        assertTrue(MarkdownDirectiveRegistry.Empty.supportsStreaming)
        assertTrue(MarkdownDirectiveRegistry.Empty.supportsStreamingFastPath)
    }

    @Test
    fun composed_source_map_should_map_through_every_transform_stage() {
        val previous = MarkdownSourceMap.Segmented(
            listOf(MarkdownSourceMap.Segmented.Segment(0, 20, 100, 120))
        )
        val current = MarkdownSourceMap.Segmented(
            listOf(MarkdownSourceMap.Segmented.Segment(0, 10, 0, 20))
        )

        val composed = composeSourceMap(previous, current)

        assertEquals(110, composed.mapOutputOffset(5))
    }

    @Test
    fun segmented_source_map_should_use_half_open_boundaries_and_map_eof() {
        val sourceMap = MarkdownSourceMap.Segmented(
            listOf(
                MarkdownSourceMap.Segmented.Segment(0, 5, 10, 15),
                MarkdownSourceMap.Segmented.Segment(5, 10, 20, 30),
            )
        )

        assertEquals(20, sourceMap.mapOutputOffset(5))
        assertEquals(30, sourceMap.mapOutputOffset(10))
    }

    private object VideoDirectivePlugin : MarkdownDirectivePlugin {
        override val id: String = "video"
        override val inputTransformers: List<MarkdownInputTransformer> = listOf(VideoSyntaxTransformer())
    }

    private class VideoSyntaxTransformer : MarkdownInputTransformer {
        override val id: String = "video-transformer"

        override fun transform(input: String): MarkdownTransformResult {
            val regex = Regex("""!VIDEO\[(.*?)\]\((.*?)\)\{poster=(.*?)\}""")
            val output = regex.replace(input) { match ->
                val title = match.groupValues[1]
                val url = match.groupValues[2]
                val poster = match.groupValues[3]
                """{% video title="$title" url="$url" poster="$poster" %}"""
            }
            return MarkdownTransformResult(output)
        }
    }

    private class RecordingDirectivePlugin(
        override val id: String,
        override val priority: Int = 0,
    ) : MarkdownDirectivePlugin {
        val renderer: MarkdownBlockDirectiveRenderer = { }
        val inlineRenderer: MarkdownInlineDirectiveRenderer = { }
        override val blockDirectiveRenderers: Map<String, MarkdownBlockDirectiveRenderer> = mapOf(
            "video" to renderer,
        )
        override val inlineDirectiveRenderers: Map<String, MarkdownInlineDirectiveRenderer> = mapOf(
            "badge" to inlineRenderer,
        )
    }
}
