package com.hrm.markdown.parser.incremental

import com.hrm.markdown.parser.LineRange
import com.hrm.markdown.parser.SourcePosition
import com.hrm.markdown.parser.SourceRange
import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.block.BlockParser
import com.hrm.markdown.parser.block.postprocessors.PostProcessorRegistry
import com.hrm.markdown.parser.core.SourceText
import com.hrm.markdown.parser.inline.InlineParser
import com.hrm.markdown.parser.streaming.InlineAutoCloser

/**
 * 核心增量解析引擎。
 *
 * 统一处理全量解析、流式追加和编辑三种场景：
 * - **全量解析**：直接调用 BlockParser 解析全部文本
 * - **流式追加**：利用脏区域追踪，只重解析尾部变化区域
 * - **编辑**：利用脏区域追踪和节点复用，精准重解析编辑影响区域
 *
 * ## 关键组件
 * - [DirtyRegionTracker]：计算脏区域
 * - [NodeReuser]：判断和复用未变化的旧节点
 * - [PostProcessorRegistry]：后处理管线
 */
class IncrementalEngine(
    private val postProcessors: PostProcessorRegistry = PostProcessorRegistry.withDefaults()
) {
    // ────── 状态 ──────
    private val fullText = StringBuilder()
    private var _document: Document = Document()
    private var _sourceText: SourceText = SourceText.of("")
    private var stableBlockCount: Int = 0
    private var stableEndLine: Int = 0
    private var lastParsedLength: Int = 0
    private var _isStreaming: Boolean = false

    private val dirtyTracker = DirtyRegionTracker()
    private val nodeReuser = NodeReuser()

    // ────── 公开属性 ──────
    val document: Document get() = _document
    val sourceText: SourceText get() = _sourceText
    val isStreaming: Boolean get() = _isStreaming

    // ────── 全量解析 ──────

    /**
     * 对给定输入执行完整解析。
     */
    fun fullParse(input: String): Document {
        fullText.clear()
        fullText.append(input)
        _isStreaming = false
        stableBlockCount = 0
        stableEndLine = 0
        lastParsedLength = 0
        return doFullParse()
    }

    // ────── 流式 API ──────

    fun beginStream() {
        fullText.clear()
        _document = Document()
        _sourceText = SourceText.of("")
        stableBlockCount = 0
        stableEndLine = 0
        lastParsedLength = 0
        _isStreaming = true
    }

    fun append(chunk: String): Document {
        if (chunk.isEmpty()) return _document
        fullText.append(chunk)
        return doIncrementalAppend()
    }

    fun endStream(): Document {
        _isStreaming = false
        return doFullParse()
    }

    fun abort(): Document {
        _isStreaming = false
        return _document
    }

    fun currentText(): String = fullText.toString()

    // ────── 编辑 API ──────

    /**
     * 应用编辑操作并增量更新 AST。
     *
     * @param edit 编辑操作
     * @return 更新后的 Document
     */
    fun applyEdit(edit: EditOperation): Document {
        val oldSource = _sourceText
        val oldText = fullText.toString()

        // 应用编辑到文本
        when (edit) {
            is EditOperation.Insert -> {
                val current = fullText.toString()
                fullText.clear()
                fullText.append(current.substring(0, edit.offset))
                fullText.append(edit.text)
                fullText.append(current.substring(edit.offset))
            }
            is EditOperation.Delete -> {
                val current = fullText.toString()
                fullText.clear()
                fullText.append(current.substring(0, edit.offset))
                fullText.append(current.substring(edit.offset + edit.length))
            }
            is EditOperation.Replace -> {
                val current = fullText.toString()
                fullText.clear()
                fullText.append(current.substring(0, edit.offset))
                fullText.append(edit.newText)
                fullText.append(current.substring(edit.offset + edit.length))
            }
            is EditOperation.Append -> {
                fullText.append(edit.text)
                // 对于 Append，委托给流式增量逻辑
                return doIncrementalAppend()
            }
        }

        val newText = fullText.toString()
        val newSource = SourceText.of(newText)
        _sourceText = newSource

        if (newSource.lineCount == 0) {
            _document = Document()
            return _document
        }

        // 计算脏区域
        val oldChildren = _document.children.toList()
        val dirtyRange = dirtyTracker.computeDirtyRange(edit, oldSource, newSource, oldChildren)

        // 找出脏区域之前可复用的节点
        val reusablePrefixCount = nodeReuser.findReusablePrefixCount(oldChildren, dirtyRange)

        // 解析脏区域（BlockParser 只做块结构 + 行内解析，后处理由 Engine 统一控制）
        val parser = BlockParser(newSource) { doc ->
            doc.linkDefinitions.putAll(_document.linkDefinitions)
            InlineParser(doc)
        }

        val newBlocks = if (dirtyRange.startLine < dirtyRange.endLine.coerceAtMost(newSource.lineCount)) {
            parser.parseLines(dirtyRange.startLine, dirtyRange.endLine.coerceAtMost(newSource.lineCount))
        } else {
            emptyList()
        }

        // 计算行数偏移
        val oldLineCount = oldSource.lineCount
        val newLineCount = newSource.lineCount
        val linesDelta = newLineCount - oldLineCount

        // 找出脏区域之后可复用的节点（需要根据旧坐标计算）
        val oldDirtyEndLine = dirtyRange.endLine - linesDelta
        val dirtyRangeOld = LineRange(dirtyRange.startLine, oldDirtyEndLine.coerceAtLeast(dirtyRange.startLine))
        val reusableSuffix = nodeReuser.findReusableSuffix(oldChildren, dirtyRangeOld, linesDelta, newSource)

        // 构建新文档
        val newDoc = Document()
        newDoc.linkDefinitions.putAll(_document.linkDefinitions)
        newDoc.footnoteDefinitions.putAll(_document.footnoteDefinitions)
        newDoc.abbreviationDefinitions.putAll(_document.abbreviationDefinitions)

        // 添加可复用的前缀
        for (i in 0 until reusablePrefixCount) {
            val child = oldChildren[i]
            child.parent = null
            newDoc.appendChild(child)
        }

        // 添加新解析的块
        for (block in newBlocks) {
            newDoc.appendChild(block)
        }

        // 添加可复用的后缀
        for (block in reusableSuffix) {
            block.parent = null
            newDoc.appendChild(block)
        }

        // 更新文档元数据
        setDocumentRanges(newDoc, newSource)

        // 后处理
        postProcessors.processAll(newDoc)

        _document = newDoc
        lastParsedLength = newText.length
        return _document
    }

    // ────── 内部实现 ──────

    /**
     * 全量解析（流结束或非流式模式）。
     */
    private fun doFullParse(): Document {
        val text = fullText.toString()
        _sourceText = SourceText.of(text)
        val parser = BlockParser(_sourceText) { doc -> InlineParser(doc) }
        _document = parser.parse()

        // 后处理统一由 Engine 控制
        postProcessors.processAll(_document)

        stableBlockCount = _document.children.size
        stableEndLine = _sourceText.lineCount
        lastParsedLength = text.length
        return _document
    }

    /**
     * append-only 增量解析（流式场景）。
     */
    private fun doIncrementalAppend(): Document {
        val text = fullText.toString()
        val newSource = SourceText.of(text)
        _sourceText = newSource

        if (newSource.lineCount == 0) {
            _document = Document()
            return _document
        }

        if (text.length == lastParsedLength) {
            return _document
        }
        lastParsedLength = text.length

        // 使用脏区域追踪器计算安全重解析起点
        val dirtyRange = dirtyTracker.computeAppendDirtyRange(stableEndLine, newSource)
        val reparseStart = dirtyRange.startLine

        // 解析脏区域（BlockParser 只做块结构 + 行内解析，后处理由 Engine 统一控制）
        val parser = BlockParser(newSource) { doc ->
            doc.linkDefinitions.putAll(_document.linkDefinitions)
            InlineParser(doc)
        }

        val tailBlocks = if (reparseStart < newSource.lineCount) {
            parser.parseLines(reparseStart, newSource.lineCount)
        } else {
            emptyList()
        }

        // 分类稳定和不稳定块
        val (nowStable, stillOpen) = classifyTailBlocks(tailBlocks, newSource)

        // 对仍在构建中的块做 auto-close 修复
        val displayBlocks = if (_isStreaming && stillOpen.isNotEmpty()) {
            autoCloseBlocks(stillOpen, newSource)
        } else {
            stillOpen
        }

        // 构建新文档
        val newDoc = Document()
        newDoc.linkDefinitions.putAll(_document.linkDefinitions)
        newDoc.footnoteDefinitions.putAll(_document.footnoteDefinitions)
        newDoc.abbreviationDefinitions.putAll(_document.abbreviationDefinitions)

        // 复用 reparseStart 之前的旧块
        val oldChildren = _document.children
        val reusableCount = oldChildren.count { child ->
            child.lineRange.endLine <= reparseStart
        }
        for (i in 0 until reusableCount) {
            val child = oldChildren[i]
            child.parent = null
            newDoc.appendChild(child)
        }

        for (block in nowStable) {
            newDoc.appendChild(block)
        }

        // 对 displayBlocks（仍在构建中的块）尝试复用旧文档中的同类型节点实例，
        // 使 Compose 层面的 === 引用比较可以跳过未变化部分的重组。
        val reusedDisplayBlocks = reuseOpenBlockInstances(displayBlocks, oldChildren)
        for (block in reusedDisplayBlocks) {
            newDoc.appendChild(block)
        }

        val newStableCount = reusableCount + nowStable.size
        stableBlockCount = newStableCount
        stableEndLine = if (nowStable.isNotEmpty()) {
            nowStable.last().lineRange.endLine
        } else if (reusableCount > 0) {
            oldChildren[reusableCount - 1].lineRange.endLine
        } else {
            0
        }

        setDocumentRanges(newDoc, newSource)

        for (block in tailBlocks) {
            collectLinkDefinitions(block, newDoc)
        }

        // 后处理
        postProcessors.processAll(newDoc)

        _document = newDoc
        return _document
    }

    // ────── 开放块实例复用 ──────

    /**
     * 对仍在构建中的 displayBlocks，尝试在旧文档的 children 中查找
     * 同 startLine + 同类型的旧节点实例。若找到，将新解析的属性写入旧实例并返回旧实例，
     * 使 Compose 的 === 引用比较判断为同一对象，跳过不必要的重组。
     *
     * 仅对 FencedCodeBlock 做复用（只有代码块在流式场景中会因反复重解析导致抖动）。
     * 其他类型直接返回新实例。
     */
    private fun reuseOpenBlockInstances(
        displayBlocks: List<Node>,
        oldChildren: List<Node>,
    ): List<Node> {
        if (displayBlocks.isEmpty() || oldChildren.isEmpty()) return displayBlocks

        return displayBlocks.map { newBlock ->
            if (newBlock !is FencedCodeBlock) return@map newBlock

            val oldBlock = oldChildren.find { old ->
                old is FencedCodeBlock &&
                        old.lineRange.startLine == newBlock.lineRange.startLine
            } as? FencedCodeBlock ?: return@map newBlock

            // 将新解析的属性写入旧实例，保持对象引用不变
            oldBlock.literal = newBlock.literal
            oldBlock.lineRange = newBlock.lineRange
            oldBlock.sourceRange = newBlock.sourceRange
            oldBlock.contentHash = newBlock.contentHash
            oldBlock.info = newBlock.info
            oldBlock.language = newBlock.language
            oldBlock.fenceChar = newBlock.fenceChar
            oldBlock.fenceLength = newBlock.fenceLength
            oldBlock.fenceIndent = newBlock.fenceIndent
            oldBlock.parent = null
            oldBlock
        }
    }

    // ────── 块稳定性分类 ──────

    private fun classifyTailBlocks(
        blocks: List<Node>,
        source: SourceText
    ): Pair<List<Node>, List<Node>> {
        if (blocks.isEmpty()) return Pair(emptyList(), emptyList())
        if (!_isStreaming) return Pair(blocks, emptyList())

        val stable = mutableListOf<Node>()
        val open = mutableListOf<Node>()

        for (i in blocks.indices) {
            if (i == blocks.size - 1) {
                val lastBlock = blocks[i]
                if (isBlockFullyClosed(lastBlock, source)) {
                    stable.add(lastBlock)
                } else {
                    open.add(lastBlock)
                }
            } else {
                val block = blocks[i]
                val nextBlock = blocks[i + 1]
                val gapStart = block.lineRange.endLine
                val gapEnd = nextBlock.lineRange.startLine
                val hasBlankSeparator = (gapStart < gapEnd) && hasBlankLineInRange(source, gapStart, gapEnd)
                if (hasBlankSeparator) {
                    stable.add(block)
                } else {
                    for (j in i until blocks.size) {
                        open.add(blocks[j])
                    }
                    break
                }
            }
        }
        return Pair(stable, open)
    }

    private fun isBlockFullyClosed(block: Node, source: SourceText): Boolean {
        val endLine = block.lineRange.endLine
        if (endLine >= source.lineCount) return false
        return hasBlankLineInRange(source, endLine, source.lineCount)
    }

    private fun hasBlankLineInRange(source: SourceText, startLine: Int, endLine: Int): Boolean {
        for (line in startLine until endLine.coerceAtMost(source.lineCount)) {
            if (source.lineContent(line).isBlank()) return true
        }
        return false
    }

    // ────── 块级自动关闭 ──────

    private fun autoCloseBlocks(blocks: List<Node>, source: SourceText): List<Node> {
        return blocks.map { block -> autoCloseBlock(block, source) }
    }

    private fun autoCloseBlock(block: Node, source: SourceText): Node {
        return when (block) {
            is FencedCodeBlock, is MathBlock, is FrontMatter, is HtmlBlock, is DiagramBlock -> block
            is Paragraph -> {
                autoCloseInlineContent(block, source)
                block
            }
            is Heading -> {
                autoCloseInlineContent(block, source)
                block
            }
            is SetextHeading -> {
                autoCloseInlineContent(block, source)
                block
            }
            is BlockQuote -> {
                val children = block.children.toList()
                if (children.isNotEmpty()) {
                    val lastChild = children.last()
                    val repaired = autoCloseBlock(lastChild, source)
                    if (repaired !== lastChild) {
                        block.replaceChild(lastChild, repaired)
                    }
                }
                block
            }
            is ListBlock, is ListItem, is CustomContainer -> {
                if (block is ContainerNode) {
                    val children = block.children.toList()
                    if (children.isNotEmpty()) {
                        val lastChild = children.last()
                        val repaired = autoCloseBlock(lastChild, source)
                        if (repaired !== lastChild) {
                            block.replaceChild(lastChild, repaired)
                        }
                    }
                }
                block
            }
            is Table -> {
                autoCloseTableCells(block, source)
                block
            }
            else -> block
        }
    }

    private fun autoCloseInlineContent(node: ContainerNode, source: SourceText) {
        val inlineText = extractInlineText(node)
        if (inlineText.isEmpty()) return

        val repairSuffix = InlineAutoCloser.buildRepairSuffix(inlineText)
        if (repairSuffix.isEmpty()) return

        val repairedContent = inlineText + repairSuffix
        val tempDoc = Document()
        tempDoc.linkDefinitions.putAll(_document.linkDefinitions)
        val inlineParser = InlineParser(tempDoc)
        node.clearChildren()
        inlineParser.parseInlines(repairedContent, node)
    }

    private fun autoCloseTableCells(table: Table, source: SourceText) {
        for (child in table.children) {
            if (child is ContainerNode) {
                for (row in child.children) {
                    if (row is TableRow) {
                        val cells = row.children.toList()
                        if (cells.isNotEmpty()) {
                            val lastCell = cells.last()
                            if (lastCell is TableCell) {
                                autoCloseInlineContent(lastCell, source)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractInlineText(node: ContainerNode): String {
        val sb = StringBuilder()
        for (child in node.children) {
            appendNodeText(child, sb)
        }
        return sb.toString()
    }

    private fun appendNodeText(node: Node, sb: StringBuilder) {
        when (node) {
            is Text -> sb.append(node.literal)
            is InlineCode -> sb.append("`").append(node.literal).append("`")
            is InlineMath -> sb.append("$").append(node.literal).append("$")
            is Emphasis -> {
                val d = node.delimiter
                sb.append(d)
                for (child in node.children) appendNodeText(child, sb)
                sb.append(d)
            }
            is StrongEmphasis -> {
                val d = node.delimiter.toString().repeat(2)
                sb.append(d)
                for (child in node.children) appendNodeText(child, sb)
                sb.append(d)
            }
            is Strikethrough -> {
                sb.append("~~")
                for (child in node.children) appendNodeText(child, sb)
                sb.append("~~")
            }
            is Highlight -> {
                sb.append("==")
                for (child in node.children) appendNodeText(child, sb)
                sb.append("==")
            }
            is Link -> {
                sb.append("[")
                for (child in node.children) appendNodeText(child, sb)
                sb.append("](").append(node.destination)
                if (node.title != null) sb.append(" \"").append(node.title).append("\"")
                sb.append(")")
            }
            is Image -> {
                sb.append("![")
                for (child in node.children) appendNodeText(child, sb)
                sb.append("](").append(node.destination)
                // 重建 =WxH 尺寸后缀
                if (node.imageWidth != null || node.imageHeight != null) {
                    sb.append(" =")
                    sb.append(node.imageWidth?.toString() ?: "")
                    sb.append("x")
                    sb.append(node.imageHeight?.toString() ?: "")
                }
                if (node.title != null) sb.append(" \"").append(node.title).append("\"")
                sb.append(")")
                // 重建属性块
                if (node.attributes.isNotEmpty()) {
                    sb.append("{")
                    for ((key, value) in node.attributes) {
                        when (key) {
                            "class" -> value.split(" ").filter { it.isNotEmpty() }.forEach { sb.append(".").append(it).append(" ") }
                            "id" -> sb.append("#").append(value).append(" ")
                            else -> if (value.isNotEmpty()) sb.append(key).append("=").append(value).append(" ") else sb.append(key).append(" ")
                        }
                    }
                    sb.trimEnd()
                    sb.append("}")
                }
            }
            is EscapedChar -> sb.append("\\").append(node.literal)
            is SoftLineBreak -> sb.append("\n")
            is HardLineBreak -> sb.append("\n")
            is HtmlEntity -> sb.append(node.literal)
            is Autolink -> sb.append("<").append(node.destination).append(">")
            is ContainerNode -> {
                for (child in node.children) appendNodeText(child, sb)
            }
            is LeafNode -> sb.append(node.literal)
        }
    }

    // ────── 工具方法 ──────

    private fun setDocumentRanges(doc: Document, source: SourceText) {
        doc.lineRange = LineRange(0, source.lineCount)
        if (source.lineCount > 0) {
            doc.sourceRange = SourceRange(
                SourcePosition(0, 0, 0),
                SourcePosition(
                    source.lineCount - 1,
                    source.lineContent(source.lineCount - 1).length,
                    source.length
                )
            )
        }
    }

    private fun collectLinkDefinitions(node: Node, doc: Document) {
        when (node) {
            is LinkReferenceDefinition -> {
                val label = node.label.lowercase().trim()
                if (label.isNotEmpty() && !doc.linkDefinitions.containsKey(label)) {
                    doc.linkDefinitions[label] = node
                }
            }
            is ContainerNode -> {
                for (child in node.children) {
                    collectLinkDefinitions(child, doc)
                }
            }
            else -> {}
        }
    }
}
