package com.hrm.markdown.renderer.internal.core.compile

import com.hrm.markdown.parser.ast.Abbreviation
import com.hrm.markdown.parser.ast.Autolink
import com.hrm.markdown.parser.ast.CitationReference
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.DirectiveInline
import com.hrm.markdown.parser.ast.Emoji
import com.hrm.markdown.parser.ast.Emphasis
import com.hrm.markdown.parser.ast.EscapedChar
import com.hrm.markdown.parser.ast.FootnoteReference
import com.hrm.markdown.parser.ast.HardLineBreak
import com.hrm.markdown.parser.ast.Highlight
import com.hrm.markdown.parser.ast.HtmlEntity
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.InlineHtml
import com.hrm.markdown.parser.ast.InlineMath
import com.hrm.markdown.parser.ast.InsertedText
import com.hrm.markdown.parser.ast.KeyboardInput
import com.hrm.markdown.parser.ast.LeafNode
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.RubyText
import com.hrm.markdown.parser.ast.SoftLineBreak
import com.hrm.markdown.parser.ast.Spoiler
import com.hrm.markdown.parser.ast.Strikethrough
import com.hrm.markdown.parser.ast.StrongEmphasis
import com.hrm.markdown.parser.ast.StyledText
import com.hrm.markdown.parser.ast.Subscript
import com.hrm.markdown.parser.ast.Superscript
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.parser.ast.WikiLink
import com.hrm.markdown.parser.SourceRange
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.core.identity.renderIdentityFromText
import com.hrm.markdown.renderer.internal.core.identity.renderIdentityFromValues
import com.hrm.markdown.renderer.internal.core.identity.renderIdentityMix
import com.hrm.markdown.renderer.internal.core.model.DirectiveInlineWidgetModel
import com.hrm.markdown.renderer.internal.core.model.ImageWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineAtom
import com.hrm.markdown.renderer.internal.core.model.InlineCodeWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineMathWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineModel
import com.hrm.markdown.renderer.internal.core.model.RubyTextWidgetModel
import com.hrm.markdown.renderer.internal.core.model.SpanMark
import com.hrm.markdown.renderer.internal.core.model.SpoilerWidgetModel
import com.hrm.markdown.renderer.internal.core.model.TextAtom
import com.hrm.markdown.renderer.internal.core.model.WidgetAtom

internal fun compileInlineModel(
    nodes: List<Node>,
    inlineRevision: Long,
    parentStableId: Long,
): InlineModel {
    val inlineStableId = stableInlineListId(parentStableId)
    return InlineModel(
        identity = RenderIdentity(
            stableId = inlineStableId,
            contentRevision = inlineRevision,
            layoutRevision = inlineRevision,
            paintRevision = 0L,
        ),
        atoms = buildList {
            compileInlineNodes(
                nodes = nodes,
                activeMarks = emptyList(),
                sink = this,
                parentStableId = inlineStableId,
            )
        }
    )
}

private fun compileInlineNodes(
    nodes: List<Node>,
    activeMarks: List<SpanMark>,
    sink: MutableList<InlineAtom>,
    parentStableId: Long,
) {
    compileNormalizedInlineSources(
        sources = InlineHtmlNormalizer.normalize(nodes),
        activeMarks = activeMarks,
        sink = sink,
        parentStableId = parentStableId,
    )
}

private fun compileNormalizedInlineSources(
    sources: List<NormalizedInlineSource>,
    activeMarks: List<SpanMark>,
    sink: MutableList<InlineAtom>,
    parentStableId: Long,
) {
    sources.forEachIndexed { index, source ->
        val stableId = stableInlineNodeId(source.anchor, parentStableId, index)
        val identity = nodeIdentity(source.anchor, stableId)
        when (source) {
            is AstInlineSource -> compileInlineNode(
                node = source.anchor,
                activeMarks = activeMarks,
                sink = sink,
                stableId = stableId,
            )
            is HtmlSpanInlineSource -> compileNormalizedInlineSources(
                sources = source.children,
                activeMarks = activeMarks + source.marks,
                sink = sink,
                parentStableId = stableId,
            )
            is HtmlLineBreakInlineSource -> sink += TextAtom(identity, "\n", activeMarks)
            is HtmlImageInlineSource -> sink += WidgetAtom(
                identity = identity,
                widget = ImageWidgetModel(
                    identity = identity,
                    url = source.source,
                    altText = source.altText,
                    title = source.title,
                    width = source.width,
                    height = source.height,
                    attributes = source.attributes,
                ),
            )
            is HiddenHtmlInlineSource -> Unit
        }
    }
}

private fun compileInlineNode(
    node: Node,
    activeMarks: List<SpanMark>,
    sink: MutableList<InlineAtom>,
    stableId: Long,
) {
    val identity = nodeIdentity(node, stableId)
    when (node) {
        is Text -> sink += TextAtom(identity, node.literal, activeMarks)
        is SoftLineBreak -> sink += TextAtom(identity, " ", activeMarks)
        is HardLineBreak -> sink += TextAtom(identity, "\n", activeMarks)
        is Emphasis -> compileInlineNodes(node.children, activeMarks + SpanMark("emphasis"), sink, stableId)
        is StrongEmphasis -> compileInlineNodes(node.children, activeMarks + SpanMark("strong"), sink, stableId)
        is Strikethrough -> compileInlineNodes(node.children, activeMarks + SpanMark("strikethrough"), sink, stableId)
        is Highlight -> compileInlineNodes(node.children, activeMarks + SpanMark("highlight"), sink, stableId)
        is Superscript -> compileInlineNodes(node.children, activeMarks + SpanMark("superscript"), sink, stableId)
        is Subscript -> compileInlineNodes(node.children, activeMarks + SpanMark("subscript"), sink, stableId)
        is InsertedText -> compileInlineNodes(node.children, activeMarks + SpanMark("inserted"), sink, stableId)
        is StyledText -> {
            compileInlineNodes(
                node.children,
                activeMarks + SpanMark(
                    kind = "styled",
                    payload = buildMap {
                        node.style?.let { put("style", it) }
                        if (node.cssClasses.isNotEmpty()) {
                            put("class", node.cssClasses.joinToString(" "))
                        }
                    }
                ),
                sink,
                stableId,
            )
        }

        is Link -> {
            compileInlineNodes(
                node.children,
                activeMarks + SpanMark(
                    kind = "link",
                    payload = mapOf(
                        "target" to node.destination,
                        "tag" to "link",
                    )
                ),
                sink,
                stableId,
            )
        }

        is Autolink -> {
            sink += TextAtom(
                identity = identity,
                text = node.destination,
                marks = activeMarks + SpanMark(
                    kind = "link",
                    payload = mapOf(
                        "target" to node.destination,
                        "tag" to "link",
                    )
                ),
            )
        }

        is WikiLink -> {
            sink += TextAtom(
                identity = identity,
                text = node.label ?: node.target,
                marks = activeMarks + SpanMark(
                    kind = "link",
                    payload = mapOf(
                        "target" to node.target,
                        "tag" to "wikilink",
                    )
                ),
            )
        }

        is InlineCode -> {
            sink += WidgetAtom(
                identity = identity,
                widget = InlineCodeWidgetModel(
                    identity = identity,
                    code = node.literal,
                )
            )
        }

        is Image -> {
            sink += WidgetAtom(
                identity = identity,
                widget = ImageWidgetModel(
                    identity = identity,
                    url = node.destination,
                    altText = extractPlainText(node),
                    title = node.title,
                    width = node.imageWidth,
                    height = node.imageHeight,
                    attributes = node.attributes,
                )
            )
        }

        is InlineMath -> {
            sink += WidgetAtom(
                identity = identity,
                widget = InlineMathWidgetModel(
                    identity = identity,
                    latex = node.literal,
                )
            )
        }

        is FootnoteReference -> {
            sink += TextAtom(
                identity = identity,
                text = "[${node.index}]",
                marks = activeMarks + SpanMark(
                    kind = "footnote",
                    payload = mapOf(
                        "label" to node.label,
                        "index" to node.index.toString(),
                    )
                ),
            )
        }

        is CitationReference -> {
            sink += TextAtom(
                identity = identity,
                text = "[${node.key}]",
                marks = activeMarks + SpanMark(
                    kind = "citation",
                    payload = mapOf("key" to node.key),
                ),
            )
        }

        is InlineHtml -> sink += TextAtom(identity, node.literal, activeMarks + SpanMark("inline_html"))
        is HtmlEntity -> sink += TextAtom(identity, node.resolved.ifEmpty { node.literal }, activeMarks)
        is EscapedChar -> sink += TextAtom(identity, node.literal, activeMarks)
        is Emoji -> sink += TextAtom(identity, node.unicode ?: node.literal.ifEmpty { ":${node.shortcode}:" }, activeMarks)
        is Abbreviation -> {
            sink += TextAtom(
                identity = identity,
                text = node.abbreviation,
                marks = activeMarks + SpanMark(
                    kind = "abbreviation",
                    payload = mapOf("fullText" to node.fullText),
                ),
            )
        }

        is KeyboardInput -> sink += TextAtom(identity, node.literal, activeMarks + SpanMark("kbd"))
        is Spoiler -> {
            sink += WidgetAtom(
                identity = identity,
                widget = SpoilerWidgetModel(
                    identity = identity,
                    content = compileInlineModel(
                        nodes = node.children,
                        inlineRevision = computeSemanticRevision(node),
                        parentStableId = stableId,
                    ),
                    alternateText = extractPlainText(node),
                ),
            )
        }

        is DirectiveInline -> {
            sink += WidgetAtom(
                identity = identity,
                widget = DirectiveInlineWidgetModel(
                    identity = identity,
                    tagName = node.tagName,
                    args = node.args,
                    alternateText = buildInlineDirectiveFallbackText(node),
                ),
            )
        }

        is RubyText -> {
            sink += WidgetAtom(
                identity = identity,
                widget = RubyTextWidgetModel(
                    identity = identity,
                    base = node.base,
                    annotation = node.annotation,
                ),
            )
        }

        else -> {
            if (node is ContainerNode) {
                compileInlineNodes(node.children, activeMarks, sink, stableId)
            }
        }
    }
}

private fun nodeIdentity(node: Node, stableId: Long): RenderIdentity {
    val revision = if (node.contentHash != 0L) {
        node.contentHash
    } else {
        renderIdentityFromValues(stableId, inlineContentFingerprint(node))
    }
    return RenderIdentity(
        stableId = stableId,
        contentRevision = revision,
        layoutRevision = revision,
        paintRevision = 0L,
    )
}

private fun stableInlineListId(parentStableId: Long): Long = renderIdentityFromValues(
    renderIdentityFromText("inline-list"),
    parentStableId,
)

private fun stableInlineNodeId(node: Node, parentStableId: Long, siblingIndex: Int): Long {
    val typeId = renderIdentityFromText(node::class.simpleName ?: "inline")
    val range = node.sourceRange
    if (range != SourceRange.EMPTY && range.length > 0) {
        return renderIdentityFromValues(
            parentStableId,
            typeId,
            range.start.offset.toLong(),
            node.lineRange.startLine.toLong(),
            siblingIndex.toLong(),
        )
    }
    return renderIdentityFromValues(parentStableId, typeId, siblingIndex.toLong())
}

private fun inlineContentFingerprint(node: Node): Long {
    return computeSemanticRevision(node)
}

private fun buildInlineDirectiveFallbackText(node: DirectiveInline): String {
    val argsText = if (node.args.isNotEmpty()) {
        " " + node.args.entries.joinToString(" ") { (key, value) ->
            if (key.startsWith("_")) value else "$key=$value"
        }
    } else {
        ""
    }
    return "{% ${node.tagName}$argsText %}"
}

private fun extractPlainText(node: Node): String = buildString {
    when (node) {
        is Text -> append(node.literal)
        is InlineCode -> append(node.literal)
        is Image -> node.children.forEach { append(extractPlainText(it)) }
        is ContainerNode -> node.children.forEach { append(extractPlainText(it)) }
        else -> Unit
    }
}
