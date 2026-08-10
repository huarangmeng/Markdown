package com.hrm.markdown.renderer

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.runtime.HtmlInlineDirectiveFallback
import com.hrm.markdown.runtime.HtmlDirectiveFallback
import com.hrm.markdown.runtime.MarkdownInputTransformer
import com.hrm.markdown.runtime.MarkdownDirectivePlugin
import com.hrm.markdown.runtime.MarkdownTransformResult
import kotlin.test.Test
import kotlin.test.assertTrue

class MarkdownHtmlDirectiveTest {
    @Test
    fun should_render_plugin_html_fallback_after_transform() {
        val html = MarkdownHtml.render(
            markdown = "!VIDEO[Demo](https://cdn.example.com/a.mp4){poster=https://cdn.example.com/a.jpg}",
            directivePlugins = listOf(VideoDirectivePlugin),
        )

        assertTrue(html.contains("""<div class="video-embed" data-url="https://cdn.example.com/a.mp4">Demo</div>"""))
    }

    @Test
    fun should_render_inline_directive_fallback_with_document_overload() {
        val document = MarkdownParser().parse("Before {% badge text=beta %} after")

        val html = MarkdownHtml.render(
            document = document,
            directivePlugins = listOf(BadgeDirectivePlugin),
        )

        assertTrue(html.contains("""<span class="badge-inline">beta</span>"""))
    }

    private object VideoDirectivePlugin : MarkdownDirectivePlugin {
        override val id: String = "video"
        override val inputTransformers: List<MarkdownInputTransformer> = listOf(VideoSyntaxTransformer())
        private val fallback = object : HtmlDirectiveFallback {
            override fun render(snapshot: com.hrm.markdown.runtime.DirectiveBlockSnapshot): String {
                val title = snapshot.args["title"].orEmpty()
                val url = snapshot.args["url"].orEmpty()
                return """<div class="video-embed" data-url="$url">$title</div>"""
            }
        }
        override val htmlDirectiveFallbacks: Map<String, HtmlDirectiveFallback> = mapOf(
            "video" to fallback
        )
    }

    private object BadgeDirectivePlugin : MarkdownDirectivePlugin {
        override val id: String = "badge"
        private val fallback = object : HtmlInlineDirectiveFallback {
            override fun render(snapshot: com.hrm.markdown.runtime.DirectiveInlineSnapshot): String {
                val text = snapshot.args["text"].orEmpty()
                return """<span class="badge-inline">$text</span>"""
            }
        }
        override val htmlInlineDirectiveFallbacks: Map<String, HtmlInlineDirectiveFallback> = mapOf(
            "badge" to fallback
        )
    }

    private class VideoSyntaxTransformer : MarkdownInputTransformer {
        override val id: String = "video-syntax"

        override fun transform(input: String): MarkdownTransformResult {
            val regex = Regex("""!VIDEO\[(.*?)\]\((.*?)\)\{poster=(.*?)\}""")
            val output = regex.replace(input) { match ->
                val title = match.groupValues[1]
                val url = match.groupValues[2]
                val poster = match.groupValues[3]
                """{% video title="$title" url="$url" poster="$poster" %}"""
            }
            return MarkdownTransformResult(markdown = output)
        }
    }
}
