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
        val sourceRevision: Long
    }

    private data class FragmentText(
        val source: String,
    ) : FragmentNode {
        override val sourceRevision: Long = renderIdentityFromText(source)
    }

    private data class FragmentStandaloneTag(
        val source: String,
        val tag: HtmlTagToken,
    ) : FragmentNode {
        override val sourceRevision: Long = renderIdentityFromText(source)
    }

    private data class FragmentElement(
        val openingSource: String,
        val closingSource: String,
        val tag: HtmlTagToken,
        val children: List<FragmentNode>,
    ) : FragmentNode {
        override val sourceRevision: Long = children.fold(
            renderIdentityFromText(openingSource)
        ) { revision, child ->
            renderIdentityFromValues(revision, child.sourceRevision)
        }
            .let { renderIdentityFromValues(it, renderIdentityFromText(closingSource)) }
    }

    private data class OpenElement(
        val openingSource: String,
        val tag: HtmlTagToken,
        val children: MutableList<FragmentNode> = mutableListOf(),
    )

    private sealed interface InlineParagraphCompileResult {
        data class Content(val model: HtmlParagraphBlockModel) : InlineParagraphCompileResult

        data object Empty : InlineParagraphCompileResult

        data object Unsupported : InlineParagraphCompileResult
    }

    fun compile(node: HtmlBlock, identity: RenderIdentity): InternalRenderBlockModel? {
        when (node.htmlType) {
            HTML_COMMENT_TYPE,
            COMMONMARK_BLOCK_TAG_TYPE,
            COMPLETE_TAG_TYPE -> Unit
            else -> return null
        }
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
            val identity = derivedIdentity(
                parent = parentIdentity,
                role = "html-inline",
                index = childIndex++,
                contentRevision = inlineBuffer.sourceRevision(),
            )
            val paragraph = compileInlineParagraph(inlineBuffer, inheritedAlignment, identity)
            inlineBuffer.clear()
            return when (paragraph) {
                is InlineParagraphCompileResult.Content -> {
                    result += paragraph.model
                    true
                }
                InlineParagraphCompileResult.Empty -> true
                InlineParagraphCompileResult.Unsupported -> false
            }
        }

        for (node in nodes) {
            when (node) {
                is FragmentText -> inlineBuffer += node
                is FragmentStandaloneTag -> {
                    val blockAction = SafeHtmlPolicy.blockAction(node.tag, inheritedAlignment)
                    if (blockAction?.role == SafeBlockHtmlRole.THEMATIC_BREAK) {
                        if (!flushInline()) return null
                        result += ThematicBreakBlockModel(
                            derivedIdentity(
                                parent = parentIdentity,
                                role = "html-hr",
                                index = childIndex++,
                                contentRevision = node.sourceRevision,
                            )
                        )
                    } else {
                        inlineBuffer += node
                    }
                }
                is FragmentElement -> {
                    val blockAction = SafeHtmlPolicy.blockAction(node.tag, inheritedAlignment)
                    if (blockAction == null) {
                        inlineBuffer += node
                    } else {
                        if (blockAction.role == SafeBlockHtmlRole.THEMATIC_BREAK) return null
                        if (!flushInline()) return null
                        val identity = derivedIdentity(
                            parent = parentIdentity,
                            role = "html-${node.tag.name.orEmpty()}",
                            index = childIndex++,
                            contentRevision = node.sourceRevision,
                        )
                        val compiled = compileContainerElement(
                            element = node,
                            action = blockAction,
                            identity = identity,
                        ) ?: return null
                        result += compiled
                    }
                }
            }
        }
        if (!flushInline()) return null
        return result
    }

    private fun compileContainerElement(
        element: FragmentElement,
        action: SafeBlockHtmlAction,
        identity: RenderIdentity,
    ): InternalRenderBlockModel? {
        return when (action.role) {
            SafeBlockHtmlRole.PARAGRAPH -> when (
                val paragraph = compileInlineParagraph(element.children, action.alignment, identity)
            ) {
                is InlineParagraphCompileResult.Content -> paragraph.model
                InlineParagraphCompileResult.Empty -> HtmlContainerBlockModel(identity, emptyList())
                InlineParagraphCompileResult.Unsupported -> null
            }
            SafeBlockHtmlRole.CONTAINER -> {
                val children = compileFlow(element.children, action.alignment, identity) ?: return null
                HtmlContainerBlockModel(identity = identity, children = children)
            }
            SafeBlockHtmlRole.THEMATIC_BREAK -> null
        }
    }

    private fun compileInlineParagraph(
        nodes: List<FragmentNode>,
        alignment: BlockTextAlignment,
        identity: RenderIdentity,
    ): InlineParagraphCompileResult {
        if (nodes.any { !it.isSupportedInlineFragment() }) {
            return InlineParagraphCompileResult.Unsupported
        }
        val astNodes = InlineAstBuilder().build(nodes)
        val inline = compileInlineModel(
            nodes = astNodes,
            inlineRevision = nodes.sourceRevision(),
        )
        if (inline.atoms.isEmpty()) return InlineParagraphCompileResult.Empty
        return InlineParagraphCompileResult.Content(
            HtmlParagraphBlockModel(
                identity = identity,
                inline = inline,
                textAlignment = alignment,
            )
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

    private fun FragmentNode.isSupportedInlineFragment(): Boolean = when (this) {
        is FragmentText -> true
        is FragmentStandaloneTag -> SafeHtmlPolicy.inlineAction(tag) != null
        is FragmentElement -> {
            SafeHtmlPolicy.inlineAction(tag) is SafeInlineHtmlAction.Span &&
                children.all { it.isSupportedInlineFragment() }
        }
    }

    private fun List<FragmentNode>.sourceRevision(): Long = fold(0L) { revision, node ->
        renderIdentityFromValues(revision, node.sourceRevision)
    }

    private fun derivedIdentity(
        parent: RenderIdentity,
        role: String,
        index: Int,
        contentRevision: Long,
    ): RenderIdentity {
        val stableId = renderIdentityFromValues(
            parent.stableId,
            renderIdentityFromText(role),
            index.toLong(),
        )
        val revision = renderIdentityFromValues(parent.contentRevision, contentRevision)
        return RenderIdentity(
            stableId = stableId,
            contentRevision = revision,
            layoutRevision = revision,
            paintRevision = 0L,
        )
    }

    private const val HTML_COMMENT_TYPE = 2
    private const val COMMONMARK_BLOCK_TAG_TYPE = 6
    private const val COMPLETE_TAG_TYPE = 7
}
