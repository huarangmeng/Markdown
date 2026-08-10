package com.hrm.markdown.renderer.internal.core.compile

import com.hrm.markdown.parser.ast.InlineHtml
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.html.HtmlTagKind
import com.hrm.markdown.parser.html.HtmlTagParser
import com.hrm.markdown.parser.html.HtmlTagToken
import com.hrm.markdown.renderer.internal.core.model.SpanMark

/**
 * Renderer 内部的行内 HTML 语义树。
 *
 * Parser 只负责识别单个 HTML token；这里负责在同一行内容器中配对标签，避免把
 * Compose 语义或错误恢复策略泄漏到 Markdown AST。
 */
internal sealed interface NormalizedInlineSource {
    val anchor: Node
}

internal data class AstInlineSource(
    override val anchor: Node,
) : NormalizedInlineSource

internal data class HtmlSpanInlineSource(
    override val anchor: InlineHtml,
    val closing: InlineHtml,
    val marks: List<SpanMark>,
    val children: List<NormalizedInlineSource>,
) : NormalizedInlineSource

internal data class HtmlLineBreakInlineSource(
    override val anchor: InlineHtml,
) : NormalizedInlineSource

internal data class HtmlImageInlineSource(
    override val anchor: InlineHtml,
    val source: String,
    val altText: String,
    val title: String?,
    val width: Int?,
    val height: Int?,
    val attributes: Map<String, String>,
) : NormalizedInlineSource

internal data class HiddenHtmlInlineSource(
    override val anchor: InlineHtml,
) : NormalizedInlineSource

/**
 * 将扁平的 InlineHtml token 线性归一化为安全的渲染语义。
 *
 * 只消费白名单内且正确配对的标签；未知标签、危险属性、错配和未闭合标签均保留为
 * [AstInlineSource]，由既有源码回退路径渲染。
 */
internal object InlineHtmlNormalizer {
    private data class OpenFrame(
        val opening: InlineHtml,
        val tagName: String,
        val marks: List<SpanMark>,
        val children: MutableList<NormalizedInlineSource> = mutableListOf(),
        var valid: Boolean = true,
    )

    fun normalize(nodes: List<Node>): List<NormalizedInlineSource> {
        val root = mutableListOf<NormalizedInlineSource>()
        val frames = ArrayDeque<OpenFrame>()

        fun append(source: NormalizedInlineSource) {
            if (frames.isEmpty()) root += source else frames.last().children += source
        }

        for (node in nodes) {
            if (node !is InlineHtml) {
                append(AstInlineSource(node))
                continue
            }

            val token = HtmlTagParser.parse(node.literal)
            if (token == null) {
                append(AstInlineSource(node))
                continue
            }

            when (token.kind) {
                HtmlTagKind.COMMENT -> append(HiddenHtmlInlineSource(node))
                HtmlTagKind.OPENING -> normalizeOpeningTag(node, token, frames, ::append)
                HtmlTagKind.SELF_CLOSING -> normalizeSelfClosingTag(node, token, ::append)
                HtmlTagKind.CLOSING -> {
                    val frame = frames.lastOrNull()
                    if (frame != null && frame.tagName == token.name) {
                        frames.removeLast()
                        if (frame.valid) {
                            append(
                                HtmlSpanInlineSource(
                                    anchor = frame.opening,
                                    closing = node,
                                    marks = frame.marks,
                                    children = frame.children,
                                )
                            )
                        } else {
                            frame.children += AstInlineSource(node)
                            frame.fallbackSources().forEach(::append)
                        }
                    } else {
                        if (token.name in SAFE_SPAN_TAGS) {
                            frames.forEach { it.valid = false }
                        }
                        append(AstInlineSource(node))
                    }
                }
                HtmlTagKind.PROCESSING_INSTRUCTION,
                HtmlTagKind.DECLARATION,
                HtmlTagKind.CDATA -> append(AstInlineSource(node))
            }
        }

        // 从内向外恢复未闭合标签，确保源码与已经归一化的内容顺序都不丢失。
        while (frames.isNotEmpty()) {
            val frame = frames.removeLast()
            val fallback = frame.fallbackSources()
            if (frames.isEmpty()) root += fallback else frames.last().children += fallback
        }

        return root
    }

    private fun normalizeOpeningTag(
        node: InlineHtml,
        token: HtmlTagToken,
        frames: ArrayDeque<OpenFrame>,
        append: (NormalizedInlineSource) -> Unit,
    ) {
        when (token.name) {
            "br" -> append(HtmlLineBreakInlineSource(node))
            "img" -> append(imageSourceOrFallback(node, token))
            else -> {
                val marks = spanMarks(token)
                if (marks == null) {
                    append(AstInlineSource(node))
                } else {
                    frames += OpenFrame(
                        opening = node,
                        tagName = token.name.orEmpty(),
                        marks = marks,
                    )
                }
            }
        }
    }

    private fun normalizeSelfClosingTag(
        node: InlineHtml,
        token: HtmlTagToken,
        append: (NormalizedInlineSource) -> Unit,
    ) {
        when (token.name) {
            "br" -> append(HtmlLineBreakInlineSource(node))
            "img" -> append(imageSourceOrFallback(node, token))
            else -> append(AstInlineSource(node))
        }
    }

    private fun imageSourceOrFallback(node: InlineHtml, token: HtmlTagToken): NormalizedInlineSource {
        val source = token.attributes["src"].orEmpty().trim()
        if (source.isEmpty() || !isSafeUrl(source, IMAGE_SCHEMES)) return AstInlineSource(node)
        return HtmlImageInlineSource(
            anchor = node,
            source = source,
            altText = token.attributes["alt"].orEmpty(),
            title = token.attributes["title"],
            width = token.attributes["width"].toHtmlDimensionOrNull(),
            height = token.attributes["height"].toHtmlDimensionOrNull(),
            attributes = token.attributes.filterKeys { it in IMAGE_ATTRIBUTES },
        )
    }

    private fun spanMarks(token: HtmlTagToken): List<SpanMark>? = when (token.name) {
        "strong", "b" -> listOf(SpanMark("strong"))
        "em", "i" -> listOf(SpanMark("emphasis"))
        "del", "s", "strike" -> listOf(SpanMark("strikethrough"))
        "mark" -> listOf(SpanMark("highlight"))
        "sup" -> listOf(SpanMark("superscript"))
        "sub" -> listOf(SpanMark("subscript"))
        "ins" -> listOf(SpanMark("inserted"))
        "u" -> listOf(SpanMark("underline"))
        "code" -> listOf(SpanMark("html_code"))
        "kbd" -> listOf(SpanMark("kbd"))
        "span" -> listOf(
            SpanMark(
                kind = "styled",
                payload = buildMap {
                    token.attributes["style"]?.let { put("style", it) }
                    token.attributes["class"]?.let { put("class", it) }
                },
            )
        )
        "a" -> {
            val target = token.attributes["href"]?.trim()
            when {
                target == null -> emptyList()
                isSafeUrl(target, LINK_SCHEMES) -> listOf(
                    SpanMark(
                        kind = "link",
                        payload = mapOf(
                            "target" to target,
                            "tag" to "link",
                        ),
                    )
                )
                else -> null
            }
        }
        else -> null
    }

    private fun String?.toHtmlDimensionOrNull(): Int? {
        val value = this?.trim()?.removeSuffix("px") ?: return null
        return value.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun isSafeUrl(value: String, allowedSchemes: Set<String>): Boolean {
        val target = value.trim()
        if (target.isEmpty() || target.any { it.code <= 0x20 }) return false

        val colon = target.indexOf(':')
        val firstPathDelimiter = listOf(target.indexOf('/'), target.indexOf('?'), target.indexOf('#'))
            .filter { it >= 0 }
            .minOrNull()
        if (colon < 0 || (firstPathDelimiter != null && colon > firstPathDelimiter)) return true

        val scheme = target.substring(0, colon)
        if (!scheme.matches(SCHEME_REGEX)) return false
        return scheme.lowercase() in allowedSchemes
    }

    private val SCHEME_REGEX = Regex("[a-zA-Z][a-zA-Z0-9+.-]*")
    private val SAFE_SPAN_TAGS = setOf(
        "strong", "b", "em", "i", "del", "s", "strike", "mark", "sup", "sub",
        "ins", "u", "code", "kbd", "span", "a",
    )
    private val LINK_SCHEMES = setOf("http", "https", "mailto", "tel")
    private val IMAGE_SCHEMES = setOf("http", "https")
    private val IMAGE_ATTRIBUTES = setOf(
        "src",
        "alt",
        "title",
        "width",
        "height",
        "class",
        "id",
        "align",
    )

    private fun OpenFrame.fallbackSources(): List<NormalizedInlineSource> = buildList {
        add(AstInlineSource(opening))
        children.forEach { addAll(it.fallbackSources()) }
    }

    private fun NormalizedInlineSource.fallbackSources(): List<NormalizedInlineSource> = when (this) {
        is AstInlineSource -> listOf(this)
        is HtmlSpanInlineSource -> buildList {
            add(AstInlineSource(anchor))
            children.forEach { addAll(it.fallbackSources()) }
            add(AstInlineSource(closing))
        }
        is HtmlLineBreakInlineSource -> listOf(AstInlineSource(anchor))
        is HtmlImageInlineSource -> listOf(AstInlineSource(anchor))
        is HiddenHtmlInlineSource -> listOf(AstInlineSource(anchor))
    }
}
