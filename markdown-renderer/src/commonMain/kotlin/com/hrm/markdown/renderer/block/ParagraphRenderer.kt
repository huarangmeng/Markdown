package com.hrm.markdown.renderer.block

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.SoftLineBreak
import com.hrm.markdown.renderer.LocalMarkdownTheme
import com.hrm.markdown.renderer.LocalRendererContext
import com.hrm.markdown.renderer.inline.rememberInlineContent

/**
 * paragraph renderer.
 * renders inline children as styled rich text.
 * if the paragraph contains only images (possibly separated by line breaks),
 * renders them as block-level image elements instead.
 */
@Composable
internal fun ParagraphRenderer(
    node: Paragraph,
    modifier: Modifier = Modifier,
) {
    val images = node.children.filter { it !is SoftLineBreak }.filterIsInstance<Image>()
    val nonImageNonBreak = node.children.filter { it !is SoftLineBreak && it !is Image }

    if (images.isNotEmpty() && nonImageNonBreak.isEmpty()) {
        // image-only paragraph: render as block-level images
        Column(modifier = modifier) {
            images.forEachIndexed { index, image ->
                ImageBlockRenderer(node = image)
                if (index < images.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    } else {
        val theme = LocalMarkdownTheme.current
        val context = LocalRendererContext.current
        val (annotated, inlineContents) = rememberInlineContent(node, context.onLinkClick)

        BasicText(
            text = annotated,
            modifier = modifier.fillMaxWidth(),
            style = theme.bodyStyle,
            inlineContent = inlineContents,
        )
    }
}
