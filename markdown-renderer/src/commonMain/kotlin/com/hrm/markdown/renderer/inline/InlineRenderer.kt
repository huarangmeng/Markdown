package com.hrm.markdown.renderer.inline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.hrm.codehigh.renderer.InlineCodeDefaults
import com.hrm.codehigh.renderer.InlineCode as CodeHighInlineCode
import com.hrm.codehigh.renderer.measureInlineCodeSize
import com.hrm.codehigh.theme.LocalCodeTheme
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.renderer.LocalCodeHighlightTheme
import com.hrm.markdown.renderer.LocalFootnoteNavigationState
import com.hrm.markdown.renderer.LocalMarkdownTheme
import com.hrm.markdown.renderer.LocalOnFootnoteClick
import com.hrm.markdown.renderer.MarkdownTheme

internal const val INLINE_PLACEHOLDER_TAG = "markdown-inline-placeholder"
internal const val INLINE_PLACEHOLDER_CHAR = '\uFFFC'

internal data class InlineContentEntry(
    val alternateText: String,
    val inlineTextContent: InlineTextContent,
)

/**
 * 将容器节点的子节点渲染为 AnnotatedString。
 * 这是行内渲染的核心：递归遍历行内 AST 节点，
 * 构建带样式标注的富文本。
 *
 * 对于无法内联的元素（如 LaTeX 行内公式），使用 InlineTextContent 机制。
 *
 * @return 包含标注文本和内联内容映射
 */
@Composable
internal fun rememberInlineContent(
    parent: ContainerNode,
    onLinkClick: ((String) -> Unit)? = null,
    hostTextStyle: TextStyle = LocalMarkdownTheme.current.bodyStyle,
): InlineContentResult {
    val theme = LocalMarkdownTheme.current
    val onFootnoteClick = LocalOnFootnoteClick.current
    val latexMeasurer = rememberLatexMeasurer()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val inlineCodeTheme = LocalCodeHighlightTheme.current ?: LocalCodeTheme.current
    return remember(parent, theme, onLinkClick, onFootnoteClick, hostTextStyle, latexMeasurer, density, textMeasurer, inlineCodeTheme) {
        val inlineContents = mutableMapOf<String, InlineContentEntry>()
        val annotated = buildAnnotatedString {
            renderInlineChildren(
                parent.children,
                theme,
                hostTextStyle,
                inlineContents,
                onLinkClick,
                onFootnoteClick,
                latexMeasurer,
                density,
                textMeasurer,
                inlineCodeTheme,
            )
        }
        InlineContentResult(
            annotated = annotated,
            inlineContents = inlineContents,
        )
    }
}

internal data class InlineContentResult(
    val annotated: AnnotatedString,
    val inlineContents: Map<String, InlineContentEntry>,
)

private fun AnnotatedString.Builder.appendInlinePlaceholder(id: String) {
    pushStringAnnotation(tag = INLINE_PLACEHOLDER_TAG, annotation = id)
    append(INLINE_PLACEHOLDER_CHAR)
    pop()
}

/**
 * 构建行内内容的 AnnotatedString（无 remember，用于嵌套调用）。
 */
internal fun buildInlineAnnotatedString(
    nodes: List<Node>,
    theme: MarkdownTheme,
    hostTextStyle: TextStyle,
    inlineContents: MutableMap<String, InlineContentEntry>,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
    latexMeasurer: LatexMeasurerState? = null,
    density: Density? = null,
    textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
    codeTheme: com.hrm.codehigh.theme.CodeTheme? = null,
): AnnotatedString = buildAnnotatedString {
        renderInlineChildren(
            nodes,
            theme,
            hostTextStyle,
            inlineContents,
            onLinkClick,
            onFootnoteClick,
            latexMeasurer,
            density,
            textMeasurer,
            codeTheme,
        )
    }

private fun AnnotatedString.Builder.renderInlineChildren(
    nodes: List<Node>,
    theme: MarkdownTheme,
    hostTextStyle: TextStyle,
    inlineContents: MutableMap<String, InlineContentEntry>,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    latexMeasurer: LatexMeasurerState? = null,
    density: Density? = null,
    textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
    inlineCodeTheme: com.hrm.codehigh.theme.CodeTheme? = null,
) {
    for (node in nodes) {
        renderInlineNode(node, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
    }
}

private fun AnnotatedString.Builder.renderInlineNode(
    node: Node,
    theme: MarkdownTheme,
    hostTextStyle: TextStyle,
    inlineContents: MutableMap<String, InlineContentEntry>,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    latexMeasurer: LatexMeasurerState? = null,
    density: Density? = null,
    textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
    inlineCodeTheme: com.hrm.codehigh.theme.CodeTheme? = null,
) {
    when (node) {
        is Text -> append(node.literal)

        is SoftLineBreak -> append(" ")

        is HardLineBreak -> append("\n")

        is Emphasis -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
            }
        }

        is StrongEmphasis -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
            }
        }

        is Strikethrough -> {
            withStyle(theme.strikethroughStyle) {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
            }
        }

        is InlineCode -> {
            if (density != null && textMeasurer != null && inlineCodeTheme != null) {
                val inlineCodeStyle = InlineCodeDefaults.style(inlineCodeTheme)
                val size = measureInlineCodeSize(
                    text = node.literal,
                    style = inlineCodeStyle,
                    density = density,
                    textMeasurer = textMeasurer,
                )
                val id = "inlinecode_${node.hashCode()}"
                appendInlinePlaceholder(id)
                val itc = InlineTextContent(
                    placeholder = Placeholder(
                        width = with(density) { size.width.toSp() },
                        height = with(density) { size.height.toSp() },
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    CodeHighInlineCode(
                        text = node.literal,
                        style = inlineCodeStyle,
                    )
                }
                inlineContents[id] = InlineContentEntry(
                    alternateText = node.literal,
                    inlineTextContent = itc,
                )
            } else {
                withStyle(theme.inlineCodeStyle) {
                    append(node.literal)
                }
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
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
            }
        }

        is Image -> {
            val id = "img_${node.hashCode()}"
            val altText = node.children.filterIsInstance<Text>().joinToString("") { it.literal }

            // 计算占位符尺寸
            val placeholderWidth = (node.imageWidth?.toFloat() ?: 200f)
            val placeholderHeight = (node.imageHeight?.toFloat() ?: 150f)

            appendInlinePlaceholder(id)
            val itc = InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderWidth.sp,
                    height = placeholderHeight.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
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
            inlineContents[id] = InlineContentEntry(
                alternateText = node.title ?: altText.ifEmpty { node.destination },
                inlineTextContent = itc,
            )
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
            val referenceText = "[${node.index}]"
            if (density != null && textMeasurer != null) {
                val referenceStyle = hostTextStyle.copy(
                    color = theme.linkColor,
                    fontSize = theme.footnoteStyle.fontSize,
                )
                val size = textMeasurer.measure(referenceText, style = referenceStyle).size
                val id = "footnote_ref_${node.hashCode()}"
                appendInlinePlaceholder(id)
                val itc = InlineTextContent(
                    placeholder = Placeholder(
                        width = with(density) { size.width.toSp() },
                        height = hostTextStyle.lineHeight,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextTop,
                    ),
                ) {
                    FootnoteReferenceContent(
                        label = node.label,
                        text = referenceText,
                        hostTextStyle = hostTextStyle,
                        theme = theme,
                        onFootnoteClick = onFootnoteClick,
                    )
                }
                inlineContents[id] = InlineContentEntry(
                    alternateText = referenceText,
                    inlineTextContent = itc,
                )
            } else {
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
                        onFootnoteClick?.invoke(node.label)
                    },
                )
                withLink(linkAnnotation) {
                    append(referenceText)
                }
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
            val placeholderHeight = if (density != null) {
                val measuredHeightPx = dims?.heightPx ?: with(density) { (fontSize * 1.6f).sp.toPx() }
                val hostHeightPx = textMeasurer?.measure("Ag", style = hostTextStyle)?.size?.height?.toFloat()
                    ?: with(density) { (hostTextStyle.lineHeight.takeUnless { it.value.isNaN() } ?: hostTextStyle.fontSize * 1.5f).toPx() }
                val extraPx = with(density) { 2f.toDp().toPx() }
                with(density) { maxOf(hostHeightPx, measuredHeightPx + extraPx).toSp() }
            } else (fontSize * 1.8f).sp

            appendInlinePlaceholder(id)
            val itc = InlineTextContent(
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
            inlineContents[id] = InlineContentEntry(
                alternateText = node.literal,
                inlineTextContent = itc,
            )
        }

        is Highlight -> {
            withStyle(SpanStyle(background = theme.highlightColor)) {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
            }
        }

        is Superscript -> {
            withStyle(
                theme.superscriptStyle.merge(
                    SpanStyle(baselineShift = BaselineShift.Superscript)
                )
            ) {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
            }
        }

        is Subscript -> {
            withStyle(
                theme.subscriptStyle.merge(
                    SpanStyle(baselineShift = BaselineShift.Subscript)
                )
            ) {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
            }
        }

        is InsertedText -> {
            withStyle(theme.insertedTextStyle) {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
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
                    renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
                }
            } else {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
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

        is CitationReference -> {
            val linkAnnotation = LinkAnnotation.Clickable(
                tag = "citation",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = theme.linkColor,
                        fontSize = theme.footnoteStyle.fontSize,
                        baselineShift = BaselineShift.Superscript,
                    ),
                ),
                linkInteractionListener = {
                    // 引用点击暂不处理，可扩展
                },
            )
            withLink(linkAnnotation) {
                append("[${node.key}]")
            }
        }

        is Spoiler -> {
            val id = "spoiler_${node.hashCode()}"
            // 提取纯文本用于估算占位符尺寸
            val plainText = extractPlainText(node)
            val fontSize = theme.bodyStyle.fontSize.value
            // 估算宽度：字符数 * 字体大小 * 0.6（中文字符更宽，取较大系数）
            val avgCharWidth = plainText.sumOf { ch ->
                if (ch.code > 0x7F) 12 else 7 // 中文字符约等宽，英文约半宽
            }.toFloat() / 10f * (fontSize / 16f)
            val placeholderWidth = (avgCharWidth + 8f).sp // 加上一点 padding
            val placeholderHeight = (fontSize * 1.5f).sp

            appendInlinePlaceholder(id)
            val itc = InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderWidth,
                    height = placeholderHeight,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                SpoilerContent(
                    node = node,
                    theme = theme,
                    hostTextStyle = hostTextStyle,
                    inlineContents = inlineContents,
                    onLinkClick = onLinkClick,
                    onFootnoteClick = onFootnoteClick,
                    latexMeasurer = latexMeasurer,
                    density = density,
                    textMeasurer = textMeasurer,
                    inlineCodeTheme = inlineCodeTheme,
                )
            }
            inlineContents[id] = InlineContentEntry(
                alternateText = plainText,
                inlineTextContent = itc,
            )
        }

        is ShortcodeInline -> {
            // 渲染行内短代码：显示标签名和参数
            withStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = theme.bodyStyle.fontSize * 0.875f,
                color = theme.linkColor,
            )) {
                val argsText = if (node.args.isNotEmpty()) {
                    " " + node.args.entries.joinToString(" ") { (k, v) ->
                        if (k.startsWith("_")) v else "$k=$v"
                    }
                } else ""
                append("{% ${node.tagName}$argsText %}")
            }
        }

        is WikiLink -> {
            val linkAnnotation = LinkAnnotation.Clickable(
                tag = "wikilink",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = theme.linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = {
                    onLinkClick?.invoke(node.target)
                },
            )
            withLink(linkAnnotation) {
                append(node.label ?: node.target)
            }
        }

        is RubyText -> {
            // 渲染 Ruby 注音：使用 InlineTextContent 机制
            val id = "ruby_${node.hashCode()}"
            val fontSize = theme.bodyStyle.fontSize.value
            // 估算宽度：基础文本字符数 * 字体大小
            val baseWidth = node.base.sumOf { ch ->
                if (ch.code > 0x7F) 12 else 7
            }.toFloat() / 10f * (fontSize / 16f)
            val placeholderWidth = (baseWidth + 2f).sp
            // 高度需要额外空间放置注音
            val placeholderHeight = (fontSize * 2.0f).sp

            appendInlinePlaceholder(id)
            val itc = InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderWidth,
                    height = placeholderHeight,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                RubyTextContent(
                    base = node.base,
                    annotation = node.annotation,
                    theme = theme,
                )
            }
            inlineContents[id] = InlineContentEntry(
                alternateText = node.base,
                inlineTextContent = itc,
            )
        }

        else -> {
            if (node is ContainerNode) {
                renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
            }
        }
    }
}

@Composable
private fun FootnoteReferenceContent(
    label: String,
    text: String,
    hostTextStyle: TextStyle,
    theme: MarkdownTheme,
    onFootnoteClick: ((String) -> Unit)?,
) {
    val footnoteNavigationState = LocalFootnoteNavigationState.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    DisposableEffect(footnoteNavigationState, label, bringIntoViewRequester) {
        footnoteNavigationState?.registerReference(label, bringIntoViewRequester)
        onDispose {
            footnoteNavigationState?.unregisterReference(label, bringIntoViewRequester)
        }
    }

    BasicText(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = theme.linkColor,
                    fontSize = theme.footnoteStyle.fontSize,
                    baselineShift = BaselineShift.Superscript,
                )
            ) {
                append(text)
            }
        },
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .clickable { onFootnoteClick?.invoke(label) },
        style = hostTextStyle,
    )
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

/**
 * 递归提取节点的纯文本内容。
 */
private fun extractPlainText(node: Node): String = buildString {
    when (node) {
        is Text -> append(node.literal)
        is InlineCode -> append(node.literal)
        is ContainerNode -> node.children.forEach { append(extractPlainText(it)) }
        else -> {}
    }
}

/**
 * 可点击的剧透文本 Composable。
 * 初始状态下文字被遮挡（文字颜色 = 背景颜色），点击后揭示文字内容。
 */
@Composable
private fun SpoilerContent(
    node: Spoiler,
    theme: MarkdownTheme,
    hostTextStyle: TextStyle,
    inlineContents: MutableMap<String, InlineContentEntry>,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    latexMeasurer: LatexMeasurerState?,
    density: Density?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer?,
    inlineCodeTheme: com.hrm.codehigh.theme.CodeTheme?,
) {
    var revealed by remember { mutableStateOf(false) }
    val annotated = remember(node, theme, revealed) {
        buildAnnotatedString {
            if (revealed) {
                withStyle(SpanStyle(
                    background = theme.spoilerColor,
                )) {
                    renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
                }
            } else {
                withStyle(SpanStyle(
                    background = theme.spoilerColor,
                    color = theme.spoilerColor,
                )) {
                    renderInlineChildren(node.children, theme, hostTextStyle, inlineContents, onLinkClick, onFootnoteClick, latexMeasurer, density, textMeasurer, inlineCodeTheme)
                }
            }
        }
    }
    BasicText(
        text = annotated,
        modifier = Modifier.clickable { revealed = !revealed },
        style = theme.bodyStyle,
    )
}

/**
 * Ruby 注音内容 Composable。
 * 将基础文本和注音文本垂直排列：注音在上，基础文本在下。
 */
@Composable
private fun RubyTextContent(
    base: String,
    annotation: String,
    theme: MarkdownTheme,
) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 注音文本（小字号，在上方）
        BasicText(
            text = annotation,
            style = theme.bodyStyle.copy(
                fontSize = theme.bodyStyle.fontSize * 0.5f,
                lineHeight = theme.bodyStyle.fontSize * 0.6f,
            ),
        )
        // 基础文本
        BasicText(
            text = base,
            style = theme.bodyStyle.copy(
                lineHeight = theme.bodyStyle.fontSize * 1.2f,
            ),
        )
    }
}
