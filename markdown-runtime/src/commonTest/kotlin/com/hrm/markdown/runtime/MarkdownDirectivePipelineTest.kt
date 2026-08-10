package com.hrm.markdown.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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
