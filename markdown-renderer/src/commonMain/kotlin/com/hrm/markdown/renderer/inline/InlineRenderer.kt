package com.hrm.markdown.renderer.inline

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.renderer.LocalMarkdownTheme
import com.hrm.markdown.renderer.MarkdownTheme

/**
 * 将容器节点的子节点渲染为 AnnotatedString。
 * 这是行内渲染的核心：递归遍历行内 AST 节点，
 * 构建带样式标注的富文本。
 *
 * 对于无法内联的元素（如 LaTeX 行内公式），使用 InlineTextContent 机制。
 *
 * @return Triple<AnnotatedString, Map<String, InlineTextContent>, Boolean>
 *         分别是标注文本、内联内容映射、是否包含链接
 */
@Composable
internal fun rememberInlineContent(
    parent: ContainerNode,
    onLinkClick: ((String) -> Unit)? = null,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val theme = LocalMarkdownTheme.current
    return remember(parent, theme, onLinkClick) {
        val inlineContents = mutableMapOf<String, InlineTextContent>()
        val annotated = buildAnnotatedString {
            renderInlineChildren(parent.children, theme, inlineContents, onLinkClick)
        }
        annotated to inlineContents
    }
}

/**
 * 构建行内内容的 AnnotatedString（无 remember，用于嵌套调用）。
 */
internal fun buildInlineAnnotatedString(
    nodes: List<Node>,
    theme: MarkdownTheme,
    inlineContents: MutableMap<String, InlineTextContent>,
    onLinkClick: ((String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    renderInlineChildren(nodes, theme, inlineContents, onLinkClick)
}

private fun AnnotatedString.Builder.renderInlineChildren(
    nodes: List<Node>,
    theme: MarkdownTheme,
    inlineContents: MutableMap<String, InlineTextContent>,
    onLinkClick: ((String) -> Unit)?,
) {
    for (node in nodes) {
        renderInlineNode(node, theme, inlineContents, onLinkClick)
    }
}

private fun AnnotatedString.Builder.renderInlineNode(
    node: Node,
    theme: MarkdownTheme,
    inlineContents: MutableMap<String, InlineTextContent>,
    onLinkClick: ((String) -> Unit)?,
) {
    when (node) {
        is Text -> append(node.literal)

        is SoftLineBreak -> append(" ")

        is HardLineBreak -> append("\n")

        is Emphasis -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }

        is StrongEmphasis -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }

        is Strikethrough -> {
            withStyle(theme.strikethroughStyle) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }

        is InlineCode -> {
            withStyle(theme.inlineCodeStyle) {
                append(node.literal)
            }
        }

        is Link -> {
            val linkAnnotation = LinkAnnotation.Clickable(
                tag = "link",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = theme.linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = {
                    onLinkClick?.invoke(node.destination)
                },
            )
            withLink(linkAnnotation) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }

        is Image -> {
            // inline images render as styled alt text with image indicator
            val altText = node.children.filterIsInstance<Text>().joinToString("") { it.literal }
            val display = altText.ifBlank { node.destination }
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = theme.linkColor)) {
                append("[")
                append(display)
                append("]")
            }
        }

        is Autolink -> {
            val linkAnnotation = LinkAnnotation.Clickable(
                tag = "link",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = theme.linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = {
                    onLinkClick?.invoke(node.destination)
                },
            )
            withLink(linkAnnotation) {
                append(node.destination)
            }
        }

        is InlineHtml -> {
            withStyle(SpanStyle(color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 14.sp)) {
                append(node.literal)
            }
        }

        is HtmlEntity -> {
            append(node.resolved.ifEmpty { node.literal })
        }

        is EscapedChar -> {
            append(node.literal)
        }

        is FootnoteReference -> {
            val linkAnnotation = LinkAnnotation.Clickable(
                tag = "footnote",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = theme.linkColor,
                        fontSize = theme.footnoteStyle.fontSize,
                        baselineShift = BaselineShift.Superscript,
                    ),
                ),
                linkInteractionListener = {
                    // 脚注点击暂不处理，可扩展
                },
            )
            withLink(linkAnnotation) {
                append("[${node.index}]")
            }
        }

        is InlineMath -> {
            val id = "math_${node.hashCode()}"
            val fontSize = theme.mathFontSize
            appendInlineContent(id, node.literal)
            inlineContents[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = (fontSize * estimateLatexWidth(node.literal)).sp,
                    height = (fontSize * 1.5f).sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                com.hrm.latex.renderer.Latex(
                    latex = node.literal,
                    config = com.hrm.latex.renderer.model.LatexConfig(
                        fontSize = fontSize.sp,
                    ),
                )
            }
        }

        is Highlight -> {
            withStyle(SpanStyle(background = theme.highlightColor)) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }

        is Superscript -> {
            withStyle(
                theme.superscriptStyle.merge(
                    SpanStyle(baselineShift = BaselineShift.Superscript)
                )
            ) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }

        is Subscript -> {
            withStyle(
                theme.subscriptStyle.merge(
                    SpanStyle(baselineShift = BaselineShift.Subscript)
                )
            ) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }

        is InsertedText -> {
            withStyle(theme.insertedTextStyle) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }

        is Emoji -> {
            append(node.literal.ifEmpty { ":${node.shortcode}:" })
        }

        else -> {
            if (node is ContainerNode) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick)
            }
        }
    }
}

/**
 * 粗略估算 LaTeX 公式的宽度比例（相对于字体大小）。
 */
private fun estimateLatexWidth(latex: String): Float {
    val baseLen = latex.length.toFloat()
    return (baseLen * 0.7f).coerceIn(1.5f, 20f)
}
