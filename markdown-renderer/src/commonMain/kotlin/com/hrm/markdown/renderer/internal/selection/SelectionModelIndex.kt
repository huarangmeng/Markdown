package com.hrm.markdown.renderer.internal.selection

import com.hrm.markdown.renderer.internal.core.model.AdmonitionBlockModel
import com.hrm.markdown.renderer.internal.core.model.BibliographyDefinitionBlockModel
import com.hrm.markdown.renderer.internal.core.model.BlockQuoteBlockModel
import com.hrm.markdown.renderer.internal.core.model.CodeBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnsLayoutBlockModel
import com.hrm.markdown.renderer.internal.core.model.CustomContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.DefinitionDescriptionBlockModel
import com.hrm.markdown.renderer.internal.core.model.DefinitionListBlockModel
import com.hrm.markdown.renderer.internal.core.model.DiagramBlockModel
import com.hrm.markdown.renderer.internal.core.model.DirectiveBlockModel
import com.hrm.markdown.renderer.internal.core.model.DirectiveInlineWidgetModel
import com.hrm.markdown.renderer.internal.core.model.FallbackContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.FigureBlockModel
import com.hrm.markdown.renderer.internal.core.model.FootnoteDefinitionBlockModel
import com.hrm.markdown.renderer.internal.core.model.HeadingBlockModel
import com.hrm.markdown.renderer.internal.core.model.HtmlBlockModel
import com.hrm.markdown.renderer.internal.core.model.ImageWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineCodeWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineMathWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineModel
import com.hrm.markdown.renderer.internal.core.model.InternalRenderBlockModel
import com.hrm.markdown.renderer.internal.core.model.ListBlockModel
import com.hrm.markdown.renderer.internal.core.model.MathBlockModel
import com.hrm.markdown.renderer.internal.core.model.PageBreakBlockModel
import com.hrm.markdown.renderer.internal.core.model.ParagraphBlockModel
import com.hrm.markdown.renderer.internal.core.model.RubyTextWidgetModel
import com.hrm.markdown.renderer.internal.core.model.SpoilerWidgetModel
import com.hrm.markdown.renderer.internal.core.model.TabBlockModel
import com.hrm.markdown.renderer.internal.core.model.TableBlockModel
import com.hrm.markdown.renderer.internal.core.model.TextAtom
import com.hrm.markdown.renderer.internal.core.model.ThematicBreakBlockModel
import com.hrm.markdown.renderer.internal.core.model.TocBlockModel
import com.hrm.markdown.renderer.internal.core.model.WidgetAtom
import com.hrm.markdown.renderer.internal.layout.inline.runPlacements
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutColumnsBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutDefinitionDescriptionGroup
import com.hrm.markdown.renderer.internal.layout.model.LayoutDefinitionListBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutFootnoteBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineRun
import com.hrm.markdown.renderer.internal.layout.model.LayoutListBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutRenderBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTabBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTableBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
import com.hrm.markdown.renderer.internal.layout.model.LayoutWidgetBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutWidgetRun

/** Lightweight, layout-independent selectable text for one document block. */
internal data class SelectionBlockEntry(
    val stableId: Long,
    val order: Int,
    val totalChars: Int,
    val text: String,
)

/** A visible layout run mapped back into its block's logical character space. */
internal data class SelectionRunSpan(
    val lineIndex: Int,
    val runIndex: Int,
    val run: LayoutInlineRun,
    val text: String,
    val charStart: Int,
    val charEnd: Int,
)

/** Heavy geometry retained only while the corresponding block is composed. */
internal data class SelectionBlockGeometry(
    val stableId: Long,
    val block: InternalLayoutBlockModel,
    val runs: List<SelectionRunSpan>,
)

/**
 * Whole-document logical selection index. It intentionally contains no layout objects or
 * [androidx.compose.ui.text.TextLayoutResult] instances, so select-all/copy remain available
 * without forcing offscreen blocks through the layout pipeline.
 */
internal class SelectionModelIndex(
    val entries: List<SelectionBlockEntry>,
) {
    private val orderByStableId = entries.associate { it.stableId to it.order }
    private val entryByStableId = entries.associateBy { it.stableId }

    val isEmpty: Boolean get() = entries.isEmpty()

    fun entryOf(stableId: Long): SelectionBlockEntry? = entryByStableId[stableId]

    fun orderOf(stableId: Long): Int? = orderByStableId[stableId]

    fun clampAnchor(anchor: SelectionAnchor): SelectionAnchor? {
        val entry = entryByStableId[anchor.blockStableId] ?: return null
        val clamped = anchor.charInBlock.coerceIn(0, entry.totalChars)
        return if (clamped == anchor.charInBlock) anchor else anchor.copy(charInBlock = clamped)
    }

    fun compare(a: SelectionAnchor, b: SelectionAnchor): Int {
        val oa = orderByStableId[a.blockStableId] ?: 0
        val ob = orderByStableId[b.blockStableId] ?: 0
        if (oa != ob) return oa.compareTo(ob)
        return a.charInBlock.compareTo(b.charInBlock)
    }

    fun normalize(a: SelectionAnchor, b: SelectionAnchor): SelectionRange =
        if (compare(a, b) <= 0) SelectionRange(a, b) else SelectionRange(b, a)

    val firstAnchor: SelectionAnchor?
        get() = entries.firstOrNull()?.let { SelectionAnchor(it.stableId, 0) }

    val lastAnchor: SelectionAnchor?
        get() = entries.lastOrNull()?.let { SelectionAnchor(it.stableId, it.totalChars) }
}

/** Build the full logical index directly from the cheap render model. */
internal fun buildSelectionIndex(blocks: List<InternalRenderBlockModel>): SelectionModelIndex {
    return IncrementalSelectionIndexBuilder().build(blocks)
}

internal data class SelectionIndexBuildMetrics(
    val computedTopLevelBlocks: Int = 0,
    val reusedTopLevelBlocks: Int = 0,
)

/**
 * Reuses the selectable-text fragments of unchanged top-level blocks across streaming snapshots.
 * Document order is still rebuilt because blocks may be inserted or moved, but plain-text
 * extraction and recursive traversal are limited to changed blocks.
 */
internal class IncrementalSelectionIndexBuilder {
    private data class RawEntry(val stableId: Long, val text: String)

    private data class CachedFragment(
        val contentRevision: Long,
        val entries: List<RawEntry>,
    )

    private var fragmentsByStableId: Map<Long, CachedFragment> = emptyMap()

    var lastMetrics: SelectionIndexBuildMetrics = SelectionIndexBuildMetrics()
        private set

    fun build(blocks: List<InternalRenderBlockModel>): SelectionModelIndex {
        val nextFragments = HashMap<Long, CachedFragment>(blocks.size)
        val entries = ArrayList<SelectionBlockEntry>()
        var computed = 0
        var reused = 0

        for (block in blocks) {
            val stableId = block.identity.stableId
            val cached = fragmentsByStableId[stableId]
            val fragment = if (cached?.contentRevision == block.identity.contentRevision) {
                reused++
                cached
            } else {
                computed++
                CachedFragment(
                    contentRevision = block.identity.contentRevision,
                    entries = selectionEntriesForBlock(block),
                )
            }
            nextFragments[stableId] = fragment
            for (entry in fragment.entries) {
                entries += SelectionBlockEntry(
                    stableId = entry.stableId,
                    order = entries.size,
                    totalChars = entry.text.length,
                    text = entry.text,
                )
            }
        }

        fragmentsByStableId = nextFragments
        lastMetrics = SelectionIndexBuildMetrics(
            computedTopLevelBlocks = computed,
            reusedTopLevelBlocks = reused,
        )
        return SelectionModelIndex(entries)
    }

    private fun selectionEntriesForBlock(block: InternalRenderBlockModel): List<RawEntry> {
        val entries = ArrayList<RawEntry>()

        fun add(stableId: Long, text: String) {
            if (text.isNotEmpty()) entries += RawEntry(stableId, text)
        }

        fun visit(current: InternalRenderBlockModel) {
            when (current) {
                is ParagraphBlockModel -> add(current.identity.stableId, current.inline.plainText())
                is HeadingBlockModel -> add(
                    current.identity.stableId,
                    buildString {
                        current.numbering?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
                        append(current.inline.plainText())
                    },
                )
                is CodeBlockModel -> add(current.identity.stableId, current.code)
                is MathBlockModel -> add(current.identity.stableId, current.latex)
                is DiagramBlockModel -> add(current.identity.stableId, current.code)
                is HtmlBlockModel -> add(current.identity.stableId, current.html)
                is TableBlockModel -> add(current.identity.stableId, current.plainText())
                is BlockQuoteBlockModel -> current.children.forEach(::visit)
                is AdmonitionBlockModel -> current.children.forEach(::visit)
                is CustomContainerBlockModel -> current.children.forEach(::visit)
                is DirectiveBlockModel -> current.children.forEach(::visit)
                is FallbackContainerBlockModel -> current.children.forEach(::visit)
                is ListBlockModel -> current.items.forEach { item -> item.children.forEach(::visit) }
                is ColumnsLayoutBlockModel -> current.columns.forEach { column -> column.children.forEach(::visit) }
                is TabBlockModel -> current.items.forEach { tab -> tab.children.forEach(::visit) }
                is FootnoteDefinitionBlockModel -> current.children.forEach(::visit)
                is DefinitionListBlockModel -> current.items.forEach { item ->
                    if (item is DefinitionDescriptionBlockModel) item.children.forEach(::visit)
                }
                is BibliographyDefinitionBlockModel,
                is FigureBlockModel,
                is PageBreakBlockModel,
                is ThematicBreakBlockModel,
                is TocBlockModel -> Unit
                else -> Unit
            }
        }

        visit(block)
        return entries
    }
}

/** Legacy/test helper that derives the logical index from already-created layout blocks. */
internal fun buildSelectionIndexFromLayout(
    blocks: List<InternalLayoutBlockModel>,
): SelectionModelIndex {
    val entries = ArrayList<SelectionBlockEntry>()
    var nextOrder = 0

    fun add(stableId: Long, text: String) {
        if (text.isEmpty()) return
        entries += SelectionBlockEntry(stableId, nextOrder++, text.length, text)
    }

    fun visit(block: InternalLayoutBlockModel) {
        when (block) {
            is LayoutInlineBlockModel -> add(
                block.identity.stableId,
                block.runPlacements().joinToString("") { placement ->
                    when (val run = placement.run) {
                        is LayoutTextRun -> run.text.text
                        is LayoutWidgetRun -> run.alternateText
                    }
                },
            )
            is LayoutTableBlockModel -> add(block.identity.stableId, block.plainText())
            is LayoutWidgetBlockModel -> add(block.identity.stableId, block.plainText())
            is LayoutRenderBlockModel -> {
                val text = (block.block as? HtmlBlockModel)?.html.orEmpty()
                if (text.isNotEmpty()) add(block.identity.stableId, text) else block.children.forEach(::visit)
            }
            is LayoutListBlockModel -> block.items.forEach { item -> item.children.forEach(::visit) }
            is LayoutColumnsBlockModel -> block.columns.forEach { column -> column.children.forEach(::visit) }
            is LayoutTabBlockModel -> block.tabs.forEach { tab -> tab.children.forEach(::visit) }
            is LayoutFootnoteBlockModel -> {
                block.leadChild?.let(::visit)
                block.trailingChildren.forEach(::visit)
            }
            is LayoutDefinitionListBlockModel -> block.items.forEach { item ->
                if (item is LayoutDefinitionDescriptionGroup) item.children.forEach(::visit)
            }
            else -> Unit
        }
    }

    blocks.forEach(::visit)
    return SelectionModelIndex(entries)
}

internal fun buildSelectionGeometry(
    block: InternalLayoutBlockModel,
    entry: SelectionBlockEntry,
): SelectionBlockGeometry {
    if (block !is LayoutInlineBlockModel) {
        return SelectionBlockGeometry(entry.stableId, block, emptyList())
    }
    val spans = ArrayList<SelectionRunSpan>()
    var fallbackCursor = 0
    for (placement in block.runPlacements()) {
        val run = placement.run
        val text = when (run) {
            is LayoutTextRun -> run.text.text
            is LayoutWidgetRun -> run.alternateText
        }
        if (text.isEmpty()) continue
        val explicitStart = when (run) {
            is LayoutTextRun -> run.sourceStart
            is LayoutWidgetRun -> run.sourceStart
        }
        val explicitEnd = when (run) {
            is LayoutTextRun -> run.sourceEnd
            is LayoutWidgetRun -> run.sourceEnd
        }
        val start = (explicitStart ?: fallbackCursor).coerceIn(0, entry.totalChars)
        val end = (explicitEnd ?: (start + text.length)).coerceIn(start, entry.totalChars)
        if (end > start) {
            spans += SelectionRunSpan(
                lineIndex = placement.lineIndex,
                runIndex = placement.runIndex,
                run = run,
                text = text.take(end - start),
                charStart = start,
                charEnd = end,
            )
        }
        fallbackCursor = end
    }
    return SelectionBlockGeometry(entry.stableId, block, spans)
}

private fun TableBlockModel.plainText(): String =
    rows.joinToString("\n") { row ->
        row.cells.joinToString("\t") { cell -> cell.inline.plainText() }
    }

private fun LayoutTableBlockModel.plainText(): String =
    rows.joinToString("\n") { row ->
        row.cells.joinToString("\t") { cell -> cell.cell?.inline?.plainText().orEmpty() }
    }

private fun LayoutWidgetBlockModel.plainText(): String =
    when (val renderBlock = block) {
        is CodeBlockModel -> renderBlock.code
        is MathBlockModel -> renderBlock.latex
        is DiagramBlockModel -> renderBlock.code
        else -> ""
    }

internal fun InlineModel.plainText(): String =
    buildString {
        for (atom in atoms) {
            when (atom) {
                is TextAtom -> append(atom.text)
                is WidgetAtom -> append(atom.widget.plainText())
            }
        }
    }

private fun com.hrm.markdown.renderer.internal.core.model.InlineWidgetModel.plainText(): String =
    when (this) {
        is InlineCodeWidgetModel -> code
        is InlineMathWidgetModel -> latex
        is ImageWidgetModel -> altText.ifEmpty { title ?: url }
        is SpoilerWidgetModel -> alternateText
        is DirectiveInlineWidgetModel -> alternateText
        is RubyTextWidgetModel -> base
    }
