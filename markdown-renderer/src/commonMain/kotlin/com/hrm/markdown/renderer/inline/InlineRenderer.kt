package com.hrm.markdown.renderer.inline

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
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
    val latexMeasurer = rememberLatexMeasurer()
    val density = LocalDensity.current
    return remember(parent, theme, onLinkClick, latexMeasurer, density) {
        val inlineContents = mutableMapOf<String, InlineTextContent>()
        val annotated = buildAnnotatedString {
            renderInlineChildren(parent.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
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
    latexMeasurer: LatexMeasurerState? = null,
    density: Density? = null,
): AnnotatedString = buildAnnotatedString {
    renderInlineChildren(nodes, theme, inlineContents, onLinkClick, latexMeasurer, density)
}

private fun AnnotatedString.Builder.renderInlineChildren(
    nodes: List<Node>,
    theme: MarkdownTheme,
    inlineContents: MutableMap<String, InlineTextContent>,
    onLinkClick: ((String) -> Unit)?,
    latexMeasurer: LatexMeasurerState? = null,
    density: Density? = null,
) {
    for (node in nodes) {
        renderInlineNode(node, theme, inlineContents, onLinkClick, latexMeasurer, density)
    }
}

private fun AnnotatedString.Builder.renderInlineNode(
    node: Node,
    theme: MarkdownTheme,
    inlineContents: MutableMap<String, InlineTextContent>,
    onLinkClick: ((String) -> Unit)?,
    latexMeasurer: LatexMeasurerState? = null,
    density: Density? = null,
) {
    when (node) {
        is Text -> append(node.literal)

        is SoftLineBreak -> append(" ")

        is HardLineBreak -> append("\n")

        is Emphasis -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
            }
        }

        is StrongEmphasis -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
            }
        }

        is Strikethrough -> {
            withStyle(theme.strikethroughStyle) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
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
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
            }
        }

        is Image -> {
            val id = "img_${node.hashCode()}"
            val altText = node.children.filterIsInstance<Text>().joinToString("") { it.literal }

            // 计算占位符尺寸
            val placeholderWidth = (node.imageWidth?.toFloat() ?: 200f)
            val placeholderHeight = (node.imageHeight?.toFloat() ?: 150f)

            appendInlineContent(id, node.title ?: altText.ifEmpty { node.destination })
            inlineContents[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderWidth.sp,
                    height = placeholderHeight.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
                ),
            ) {
                val imageData = com.hrm.markdown.renderer.MarkdownImageData(
                    url = node.destination,
                    altText = altText,
                    title = node.title,
                    width = node.imageWidth,
                    height = node.imageHeight,
                    attributes = node.attributes,
                )
                val customRenderer = com.hrm.markdown.renderer.LocalImageRenderer.current
                if (customRenderer != null) {
                    customRenderer(imageData, androidx.compose.ui.Modifier)
                } else {
                    com.hrm.markdown.renderer.DefaultMarkdownImage(
                        data = imageData,
                    )
                }
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
            val latexConfig = LatexConfig(
                fontSize = fontSize.sp,
                color = theme.mathColor,
                darkColor = theme.mathColor,
            )

            // 使用 LatexMeasurer 精确测量公式尺寸，避免空白
            val dims = latexMeasurer?.measure(node.literal, latexConfig)
            val placeholderWidth = if (dims != null && density != null) {
                with(density) { dims.widthPx.toSp() }
            } else {
                // 回退：粗略估算
                (fontSize * estimateLatexWidth(node.literal)).sp
            }
            val placeholderHeight = if (dims != null && density != null) {
                with(density) { dims.heightPx.toSp() }
            } else {
                (fontSize * 1.5f).sp
            }

            appendInlineContent(id, node.literal)
            inlineContents[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderWidth,
                    height = placeholderHeight,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                com.hrm.latex.renderer.Latex(
                    latex = node.literal,
                    config = latexConfig,
                )
            }
        }

        is Highlight -> {
            withStyle(SpanStyle(background = theme.highlightColor)) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
            }
        }

        is Superscript -> {
            withStyle(
                theme.superscriptStyle.merge(
                    SpanStyle(baselineShift = BaselineShift.Superscript)
                )
            ) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
            }
        }

        is Subscript -> {
            withStyle(
                theme.subscriptStyle.merge(
                    SpanStyle(baselineShift = BaselineShift.Subscript)
                )
            ) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
            }
        }

        is InsertedText -> {
            withStyle(theme.insertedTextStyle) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
            }
        }

        is Emoji -> {
            // 优先显示 unicode（已映射），否则显示 literal
            append(node.unicode ?: node.literal.ifEmpty { ":${node.shortcode}:" })
        }

        is StyledText -> {
            // 从属性中提取样式信息
            val styleStr = node.style
            val spanStyle = if (styleStr != null) {
                parseCssStyleToSpanStyle(styleStr, theme)
            } else {
                // 根据 CSS class 推断样式
                inferStyleFromClasses(node.cssClasses, theme)
            }
            if (spanStyle != null) {
                withStyle(spanStyle) {
                    renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
                }
            } else {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
            }
        }

        is Abbreviation -> {
            // embed fullText as annotation so consumers can show tooltip on hover/click
            if (node.fullText.isNotEmpty()) {
                pushStringAnnotation(tag = "abbreviation", annotation = node.fullText)
                withStyle(theme.abbreviationStyle) {
                    append(node.abbreviation)
                }
                pop()
            } else {
                withStyle(theme.abbreviationStyle) {
                    append(node.abbreviation)
                }
            }
        }

        is KeyboardInput -> {
            // 渲染键盘按键：等宽字体 + 背景
            withStyle(theme.kbdStyle) {
                append(node.literal)
            }
        }

        else -> {
            if (node is ContainerNode) {
                renderInlineChildren(node.children, theme, inlineContents, onLinkClick, latexMeasurer, density)
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

/**
 * 简易 CSS style 字符串转 SpanStyle。
 * 支持常见属性：color, background, font-weight, font-style, text-decoration, font-size。
 */
private fun parseCssStyleToSpanStyle(css: String, theme: MarkdownTheme): SpanStyle? {
    if (css.isBlank()) return null
    var color: Color? = null
    var background: Color? = null
    var fontWeight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var textDecoration: TextDecoration? = null

    val pairs = css.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    for (pair in pairs) {
        val colonIdx = pair.indexOf(':')
        if (colonIdx < 0) continue
        val key = pair.substring(0, colonIdx).trim().lowercase()
        val value = pair.substring(colonIdx + 1).trim().lowercase()
        when (key) {
            "color" -> color = parseCssColor(value)
            "background", "background-color" -> background = parseCssColor(value)
            "font-weight" -> fontWeight = when (value) {
                "bold" -> FontWeight.Bold
                "normal" -> FontWeight.Normal
                "lighter" -> FontWeight.Light
                else -> null
            }
            "font-style" -> fontStyle = when (value) {
                "italic" -> FontStyle.Italic
                "normal" -> FontStyle.Normal
                else -> null
            }
            "text-decoration" -> textDecoration = when {
                "underline" in value -> TextDecoration.Underline
                "line-through" in value -> TextDecoration.LineThrough
                else -> null
            }
        }
    }
    return SpanStyle(
        color = color ?: Color.Unspecified,
        background = background ?: Color.Unspecified,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textDecoration = textDecoration,
    )
}

/**
 * 简易 CSS 颜色解析（支持命名色和 hex）。
 */
private fun parseCssColor(value: String): Color? {
    return when (value.trim().lowercase()) {
        "red" -> Color.Red
        "blue" -> Color.Blue
        "green" -> Color.Green
        "yellow" -> Color.Yellow
        "white" -> Color.White
        "black" -> Color.Black
        "gray", "grey" -> Color.Gray
        "cyan" -> Color.Cyan
        "magenta" -> Color.Magenta
        "orange" -> Color(0xFFFFA500)
        "purple" -> Color(0xFF800080)
        "pink" -> Color(0xFFFF69B4)
        else -> {
            // 尝试 hex 颜色
            val hex = value.removePrefix("#")
            when (hex.length) {
                6 -> try { Color(("FF$hex").toLong(16)) } catch (_: Exception) { null }
                8 -> try { Color(hex.toLong(16)) } catch (_: Exception) { null }
                3 -> try {
                    val r = hex[0].toString().repeat(2)
                    val g = hex[1].toString().repeat(2)
                    val b = hex[2].toString().repeat(2)
                    Color(("FF$r$g$b").toLong(16))
                } catch (_: Exception) { null }
                else -> null
            }
        }
    }
}

/**
 * 根据 CSS class 名推断 SpanStyle。
 * 支持常见约定类名：red, blue, green, bold, italic, underline, highlight 等。
 */
private fun inferStyleFromClasses(classes: List<String>, theme: MarkdownTheme): SpanStyle? {
    if (classes.isEmpty()) return null
    var color: Color? = null
    var background: Color? = null
    var fontWeight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var textDecoration: TextDecoration? = null

    for (cls in classes) {
        when (cls.lowercase()) {
            "red" -> color = Color.Red
            "blue" -> color = Color.Blue
            "green" -> color = Color.Green
            "yellow" -> color = Color.Yellow
            "orange" -> color = Color(0xFFFFA500)
            "purple" -> color = Color(0xFF800080)
            "pink" -> color = Color(0xFFFF69B4)
            "gray", "grey" -> color = Color.Gray
            "bold" -> fontWeight = FontWeight.Bold
            "italic" -> fontStyle = FontStyle.Italic
            "underline" -> textDecoration = TextDecoration.Underline
            "line-through", "strikethrough" -> textDecoration = TextDecoration.LineThrough
            "highlight" -> background = theme.highlightColor
        }
    }
    return SpanStyle(
        color = color ?: Color.Unspecified,
        background = background ?: Color.Unspecified,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textDecoration = textDecoration,
    )
}
