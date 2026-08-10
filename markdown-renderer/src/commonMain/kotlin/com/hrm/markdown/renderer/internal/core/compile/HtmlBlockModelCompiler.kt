package com.hrm.markdown.renderer.internal.core.compile

import com.hrm.markdown.parser.ast.HtmlBlock
import com.hrm.markdown.parser.ast.InlineHtml
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.parser.core.HtmlEntities
import com.hrm.markdown.parser.html.HtmlFragmentToken
import com.hrm.markdown.parser.html.HtmlFragmentTokenizer
import com.hrm.markdown.parser.html.HtmlTagCategories
import com.hrm.markdown.parser.html.HtmlTagKind
import com.hrm.markdown.parser.html.HtmlTagToken
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.core.identity.renderIdentityFromText
import com.hrm.markdown.renderer.internal.core.identity.renderIdentityFromValues
import com.hrm.markdown.renderer.internal.core.model.BlockTextAlignment
import com.hrm.markdown.renderer.internal.core.model.HtmlContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.HtmlParagraphBlockModel
import com.hrm.markdown.renderer.internal.core.model.InlineModel
import com.hrm.markdown.renderer.internal.core.model.InternalRenderBlockModel
import com.hrm.markdown.renderer.internal.core.model.ThematicBreakBlockModel

/** 将受支持的 HtmlBlock fragment 编译为平台无关 RenderModel。 */
internal object HtmlBlockModelCompiler {
    private sealed interface FragmentNode {
        val source: String
    }

    private data class FragmentText(
        override val source: String,
    ) : FragmentNode

    private data class FragmentStandaloneTag(
        override val source: String,
        val tag: HtmlTagToken,
    ) : FragmentNode

    private data class FragmentElement(
        val openingSource: String,
        val closingSource: String,
        val tag: HtmlTagToken,
        val children: List<FragmentNode>,
    ) : FragmentNode {
        override val source: String = buildString {
            append(openingSource)
            children.forEach { append(it.source) }
            append(closingSource)
        }
    }

    private data class OpenElement(
        val openingSource: String,
        val tag: HtmlTagToken,
        val children: MutableList<FragmentNode> = mutableListOf(),
    )

    fun compile(node: HtmlBlock, identity: RenderIdentity): InternalRenderBlockModel? {
        val tokens = HtmlFragmentTokenizer.tokenize(node.literal) ?: return null
        val tree = buildTree(tokens) ?: return null
        val children = compileFlow(
            nodes = tree,
            inheritedAlignment = BlockTextAlignment.INHERIT,
            parentIdentity = identity,
        ) ?: return null
        return HtmlContainerBlockModel(identity = identity, children = children)
    }

    private fun buildTree(tokens: List<HtmlFragmentToken>): List<FragmentNode>? {
        val root = mutableListOf<FragmentNode>()
        val frames = ArrayDeque<OpenElement>()

        fun append(node: FragmentNode) {
            if (frames.isEmpty()) root += node else frames.last().children += node
        }

        for (token in tokens) {
            when (token) {
                is HtmlFragmentToken.Text -> append(FragmentText(token.literal))
                is HtmlFragmentToken.Tag -> when (token.tag.kind) {
                    HtmlTagKind.OPENING -> {
                        val name = token.tag.name.orEmpty()
                        if (HtmlTagCategories.isVoidTag(name)) {
                            append(FragmentStandaloneTag(token.literal, token.tag))
                        } else {
                            frames += OpenElement(token.literal, token.tag)
                        }
                    }
                    HtmlTagKind.SELF_CLOSING,
                    HtmlTagKind.COMMENT,
                    HtmlTagKind.PROCESSING_INSTRUCTION,
                    HtmlTagKind.DECLARATION,
                    HtmlTagKind.CDATA -> append(FragmentStandaloneTag(token.literal, token.tag))
                    HtmlTagKind.CLOSING -> {
                        val frame = frames.lastOrNull() ?: return null
                        if (frame.tag.name != token.tag.name) return null
                        frames.removeLast()
                        append(
                            FragmentElement(
                                openingSource = frame.openingSource,
                                closingSource = token.literal,
                                tag = frame.tag,
                                children = frame.children,
                            )
                        )
                    }
                }
            }
        }
        if (frames.isNotEmpty()) return null
        return root
    }

    private fun compileFlow(
        nodes: List<FragmentNode>,
        inheritedAlignment: BlockTextAlignment,
        parentIdentity: RenderIdentity,
    ): List<InternalRenderBlockModel>? {
        val result = mutableListOf<InternalRenderBlockModel>()
        val inlineBuffer = mutableListOf<FragmentNode>()
        var childIndex = 0

        fun flushInline(): Boolean {
            if (inlineBuffer.isEmpty()) return true
            val identity = derivedIdentity(parentIdentity, "html-inline", childIndex++, inlineBuffer.sourceText())
            val paragraph = compileInlineParagraph(inlineBuffer, inheritedAlignment, identity)
            inlineBuffer.clear()
            if (paragraph != null) result += paragraph
            return true
        }

        for (node in nodes) {
            val blockName = node.blockTagName()
            when {
                blockName == null -> inlineBuffer += node
                blockName == "hr" && node is FragmentStandaloneTag -> {
                    flushInline()
                    result += ThematicBreakBlockModel(
                        derivedIdentity(parentIdentity, "html-hr", childIndex++, node.source)
                    )
                }
                node is FragmentElement && blockName in SAFE_CONTAINER_TAGS -> {
                    flushInline()
                    val identity = derivedIdentity(parentIdentity, "html-$blockName", childIndex++, node.source)
                    val compiled = compileContainerElement(node, inheritedAlignment, identity) ?: return null
                    result += compiled
                }
                else -> return null
            }
        }
        flushInline()
        return result
    }

    private fun compileContainerElement(
        element: FragmentElement,
        inheritedAlignment: BlockTextAlignment,
        identity: RenderIdentity,
    ): InternalRenderBlockModel? {
        val name = element.tag.name.orEmpty()
        val alignment = element.resolveAlignment(inheritedAlignment)
        return when (name) {
            "p" -> compileInlineParagraph(element.children, alignment, identity)
                ?: HtmlContainerBlockModel(identity, emptyList())
            else -> {
                val children = compileFlow(element.children, alignment, identity) ?: return null
                HtmlContainerBlockModel(identity = identity, children = children)
            }
        }
    }

    private fun compileInlineParagraph(
        nodes: List<FragmentNode>,
        alignment: BlockTextAlignment,
        identity: RenderIdentity,
    ): HtmlParagraphBlockModel? {
        if (nodes.any { it.containsBlockTag() }) return null
        val astNodes = InlineAstBuilder().build(nodes)
        val inline = compileInlineModel(
            nodes = astNodes,
            inlineRevision = renderIdentityFromText(nodes.sourceText()),
        )
        if (inline.atoms.isEmpty()) return null
        return HtmlParagraphBlockModel(
            identity = identity,
            inline = inline,
            textAlignment = alignment,
        )
    }

    private class InlineAstBuilder {
        private val nodes = mutableListOf<Node>()
        private val text = StringBuilder()
        private var pendingWhitespace = false
        private var hasVisibleContentOnLine = false

        fun build(source: List<FragmentNode>): List<Node> {
            source.forEach(::append)
            flushText()
            return nodes
        }

        private fun append(node: FragmentNode) {
            when (node) {
                is FragmentText -> appendText(HtmlEntities.replaceAll(node.source))
                is FragmentStandaloneTag -> appendTag(node.source, node.tag)
                is FragmentElement -> {
                    appendTag(node.openingSource, node.tag)
                    node.children.forEach(::append)
                    appendTag(
                        node.closingSource,
                        HtmlTagToken(kind = HtmlTagKind.CLOSING, name = node.tag.name),
                    )
                }
            }
        }

        private fun appendText(value: String) {
            for (char in value) {
                if (char.isWhitespace()) {
                    pendingWhitespace = true
                } else {
                    if (pendingWhitespace && hasVisibleContentOnLine) text.append(' ')
                    text.append(char)
                    pendingWhitespace = false
                    hasVisibleContentOnLine = true
                }
            }
        }

        private fun appendTag(literal: String, tag: HtmlTagToken) {
            flushText()
            nodes += InlineHtml(literal)
            when (tag.name) {
                "br" -> {
                    pendingWhitespace = false
                    hasVisibleContentOnLine = false
                }
                "img" -> {
                    pendingWhitespace = false
                    hasVisibleContentOnLine = true
                }
            }
        }

        private fun flushText() {
            if (text.isEmpty()) return
            nodes += Text(text.toString())
            text.clear()
        }
    }

    private fun FragmentElement.resolveAlignment(inherited: BlockTextAlignment): BlockTextAlignment {
        if (tag.name == "center") return BlockTextAlignment.CENTER
        val explicit = tag.attributes["align"] ?: tag.attributes["style"]
            ?.split(';')
            ?.mapNotNull { declaration ->
                val separator = declaration.indexOf(':')
                if (separator < 0) return@mapNotNull null
                val name = declaration.substring(0, separator).trim().lowercase()
                val value = declaration.substring(separator + 1).trim()
                value.takeIf { name == "text-align" }
            }
            ?.lastOrNull()
        return when (explicit?.trim()?.lowercase()) {
            "left", "start" -> BlockTextAlignment.START
            "center" -> BlockTextAlignment.CENTER
            "right", "end" -> BlockTextAlignment.END
            else -> inherited
        }
    }

    private fun FragmentNode.blockTagName(): String? = when (this) {
        is FragmentElement -> tag.name?.takeIf(HtmlTagCategories::isBlockTag)
        is FragmentStandaloneTag -> tag.name?.takeIf(HtmlTagCategories::isBlockTag)
        is FragmentText -> null
    }

    private fun FragmentNode.containsBlockTag(): Boolean = when (this) {
        is FragmentElement -> HtmlTagCategories.isBlockTag(tag.name.orEmpty()) || children.any { it.containsBlockTag() }
        is FragmentStandaloneTag -> tag.name?.let(HtmlTagCategories::isBlockTag) == true
        is FragmentText -> false
    }

    private fun List<FragmentNode>.sourceText(): String = joinToString(separator = "") { it.source }

    private fun derivedIdentity(
        parent: RenderIdentity,
        role: String,
        index: Int,
        content: String,
    ): RenderIdentity {
        val stableId = renderIdentityFromValues(
            parent.stableId,
            renderIdentityFromText(role),
            index.toLong(),
        )
        val revision = renderIdentityFromText(content, parent.contentRevision)
        return RenderIdentity(
            stableId = stableId,
            contentRevision = revision,
            layoutRevision = revision,
            paintRevision = 0L,
        )
    }

    private val SAFE_CONTAINER_TAGS = setOf(
        "article",
        "center",
        "div",
        "footer",
        "header",
        "main",
        "p",
        "section",
    )
}
