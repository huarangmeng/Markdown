package com.hrm.markdown.renderer.internal.selection

import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutColumnsBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutDefinitionDescriptionGroup
import com.hrm.markdown.renderer.internal.layout.model.LayoutDefinitionListBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutFootnoteBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutListBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutRenderBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTabBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun

/**
 * 单个 LayoutTextRun 在所属 block 字符空间内占据的区间 `[charStart, charEnd)`。
 * [lineIndex]/[runIndex] 用于命中测试与高亮时回到几何模型定位。
 */
internal data class SelectionRunSpan(
    val lineIndex: Int,
    val runIndex: Int,
    val run: LayoutTextRun,
    val charStart: Int,
    val charEnd: Int,
)

/**
 * 一个可选中的 inline 块在文档中的索引条目。
 * [order] 为深度优先访问顺序；[totalChars] 为块内全部文本长度。
 */
internal data class SelectionBlockEntry(
    val stableId: Long,
    val order: Int,
    val block: LayoutInlineBlockModel,
    val runs: List<SelectionRunSpan>,
    val totalChars: Int,
) {
    val text: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString { for (span in runs) append(span.run.text) }
    }
}

/**
 * 选区文档模型索引：把整棵 layout 树里所有可选 inline 块按文档序拍平，
 * 提供锚点比较、归一化、夹紧、字符→run 定位等纯函数能力。
 */
internal class SelectionModelIndex(
    val entries: List<SelectionBlockEntry>,
) {
    private val orderByStableId: Map<Long, Int> =
        entries.associate { it.stableId to it.order }
    private val entryByStableId: Map<Long, SelectionBlockEntry> =
        entries.associateBy { it.stableId }

    val isEmpty: Boolean get() = entries.isEmpty()

    fun entryOf(stableId: Long): SelectionBlockEntry? = entryByStableId[stableId]

    fun orderOf(stableId: Long): Int? = orderByStableId[stableId]

    /** 把锚点的 charInBlock 夹紧到所属块的合法范围；块不存在则返回 null。 */
    fun clampAnchor(anchor: SelectionAnchor): SelectionAnchor? {
        val entry = entryByStableId[anchor.blockStableId] ?: return null
        val clamped = anchor.charInBlock.coerceIn(0, entry.totalChars)
        return if (clamped == anchor.charInBlock) anchor else anchor.copy(charInBlock = clamped)
    }

    /** 文档序比较：先比 order，再比块内偏移。块不存在时按 0 处理。 */
    fun compare(a: SelectionAnchor, b: SelectionAnchor): Int {
        val oa = orderByStableId[a.blockStableId] ?: 0
        val ob = orderByStableId[b.blockStableId] ?: 0
        if (oa != ob) return oa.compareTo(ob)
        return a.charInBlock.compareTo(b.charInBlock)
    }

    /** 顺序无关地构造规范范围（start 不晚于 end）。 */
    fun normalize(a: SelectionAnchor, b: SelectionAnchor): SelectionRange =
        if (compare(a, b) <= 0) SelectionRange(a, b) else SelectionRange(b, a)

    /** 把块内字符偏移定位到具体 run 及 run 内偏移。 */
    fun charToRun(entry: SelectionBlockEntry, charInBlock: Int): Pair<SelectionRunSpan, Int>? {
        if (entry.runs.isEmpty()) return null
        val clamped = charInBlock.coerceIn(0, entry.totalChars)
        for (span in entry.runs) {
            if (clamped < span.charEnd) {
                return span to (clamped - span.charStart).coerceAtLeast(0)
            }
        }
        val last = entry.runs.last()
        return last to (last.charEnd - last.charStart)
    }

    val firstAnchor: SelectionAnchor?
        get() = entries.firstOrNull()?.let { SelectionAnchor(it.stableId, 0) }

    val lastAnchor: SelectionAnchor?
        get() = entries.lastOrNull()?.let { SelectionAnchor(it.stableId, it.totalChars) }
}

/**
 * 深度优先遍历 layout 树，收集所有可选 inline 块。
 * 跳过表格（排除 cell）、widget 块及 figure/toc/bibliography 等纯渲染块。
 */
internal fun buildSelectionIndex(blocks: List<InternalLayoutBlockModel>): SelectionModelIndex {
    val entries = ArrayList<SelectionBlockEntry>()
    var nextOrder = 0

    fun visit(block: InternalLayoutBlockModel) {
        when (block) {
            is LayoutInlineBlockModel -> {
                entries += buildBlockEntry(block, nextOrder++)
            }

            is LayoutRenderBlockModel -> block.children.forEach(::visit)
            is LayoutListBlockModel -> block.items.forEach { item -> item.children.forEach(::visit) }
            is LayoutColumnsBlockModel -> block.columns.forEach { col -> col.children.forEach(::visit) }
            is LayoutTabBlockModel -> block.tabs.forEach { tab -> tab.children.forEach(::visit) }
            is LayoutFootnoteBlockModel -> {
                block.leadChild?.let(::visit)
                block.trailingChildren.forEach(::visit)
            }
            is LayoutDefinitionListBlockModel -> block.items.forEach { item ->
                if (item is LayoutDefinitionDescriptionGroup) item.children.forEach(::visit)
            }
            // Excluded from selection (MVP): table cells, widget blocks, figure/toc/bibliography.
            else -> Unit
        }
    }

    blocks.forEach(::visit)
    return SelectionModelIndex(entries)
}

private fun buildBlockEntry(block: LayoutInlineBlockModel, order: Int): SelectionBlockEntry {
    val runs = ArrayList<SelectionRunSpan>()
    var cursor = 0
    block.lines.forEachIndexed { lineIndex, line ->
        line.runs.forEachIndexed { runIndex, run ->
            if (run is LayoutTextRun) {
                val len = run.text.length
                runs += SelectionRunSpan(
                    lineIndex = lineIndex,
                    runIndex = runIndex,
                    run = run,
                    charStart = cursor,
                    charEnd = cursor + len,
                )
                cursor += len
            }
        }
    }
    return SelectionBlockEntry(
        stableId = block.identity.stableId,
        order = order,
        block = block,
        runs = runs,
        totalChars = cursor,
    )
}
