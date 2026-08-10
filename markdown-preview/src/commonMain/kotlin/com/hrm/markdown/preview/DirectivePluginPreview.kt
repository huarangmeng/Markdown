package com.hrm.markdown.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.runtime.MarkdownBlockDirectiveRenderer
import com.hrm.markdown.runtime.MarkdownInputTransformer
import com.hrm.markdown.runtime.MarkdownDirectivePlugin
import com.hrm.markdown.runtime.MarkdownTransformResult

internal val directivePluginPreviewGroups = listOf(
    PreviewGroup(
        id = "plugins_video",
        title = "插件：Video 语法 + 原生渲染",
        description = "外部特殊语法通过 transformer 转为 directive，然后命中 block renderer 渲染",
        items = listOf(
            PreviewItem(
                id = "plugin_video_basic",
                title = "Video 插件示例",
                content = {
                    Markdown(
                        markdown = """
这里是自定义语法：

!VIDEO[Demo](https://cdn.example.com/a.mp4){poster=https://cdn.example.com/a.jpg}

后面继续是普通 Markdown。
                        """.trimIndent(),
                        directivePlugins = listOf(VideoDirectivePlugin),
                    )
                }
            ),
        )
    )
)

/**
 * Demo: 外部插件只通过“注册”完成：
 * - 自定义语法识别：transformer
 * - 原生渲染：directive block renderer
 */
private object VideoDirectivePlugin : MarkdownDirectivePlugin {
    override val id: String = "video"
    override val inputTransformers: List<MarkdownInputTransformer> = listOf(VideoSyntaxTransformer())
    override val blockDirectiveRenderers: Map<String, MarkdownBlockDirectiveRenderer> = mapOf(
        "video" to { scope ->
            VideoCard(
                title = scope.directive.args["title"] ?: "Untitled",
                url = scope.directive.args["url"] ?: "",
                poster = scope.directive.args["poster"],
            )
        }
    )
}

private class VideoSyntaxTransformer : MarkdownInputTransformer {
    override val id: String = "video-syntax"

    override fun transform(input: String): MarkdownTransformResult {
        // Very small demo transformer:
        // !VIDEO[Title](url){poster=posterUrl} -> {% video title="Title" url="..." poster="..." %}
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

@Composable
private fun VideoCard(
    title: String,
    url: String,
    poster: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101418), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Video (native)",
            color = Color(0xFF88C0FF),
            fontFamily = FontFamily.Monospace,
        )
        Text(text = "title: $title", color = Color(0xFFE6E6E6))
        Text(text = "url: $url", color = Color(0xFFE6E6E6))
        if (poster != null) {
            Text(text = "poster: $poster", color = Color(0xFFE6E6E6))
        }
    }
}
