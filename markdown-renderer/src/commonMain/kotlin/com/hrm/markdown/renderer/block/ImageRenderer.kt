package com.hrm.markdown.renderer.block

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.Text as TextNode
import com.hrm.markdown.renderer.LocalMarkdownTheme
import com.hrm.markdown.renderer.LocalRendererContext

/**
 * block-level image renderer.
 * delegates to the custom imageRenderer from context if provided,
 * otherwise shows the alt text as a styled placeholder.
 */
@Composable
internal fun ImageBlockRenderer(
    node: Image,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMarkdownTheme.current
    val context = LocalRendererContext.current
    val altText = node.children.filterIsInstance<TextNode>().joinToString("") { it.literal }
    val url = node.destination
    val title = node.title

    val imageModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(theme.imageCornerRadius))

    if (context.imageRenderer != null) {
        context.imageRenderer.invoke(url, altText, title, imageModifier)
    } else {
        // default fallback: show alt text in a styled box
        Box(
            modifier = imageModifier
                .background(theme.codeBlockBackground)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = altText.ifBlank { url },
                style = theme.bodyStyle.copy(
                    fontStyle = FontStyle.Italic,
                    color = theme.imageErrorColor,
                ),
            )
        }
    }
}
