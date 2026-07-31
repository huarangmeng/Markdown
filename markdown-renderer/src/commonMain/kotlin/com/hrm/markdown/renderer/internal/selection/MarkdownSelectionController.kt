package com.hrm.markdown.renderer.internal.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import com.hrm.markdown.renderer.internal.core.model.InternalRenderBlockModel
import com.hrm.markdown.renderer.internal.layout.inline.LayoutInlineRunPlacement
import com.hrm.markdown.renderer.internal.layout.inline.textMeasurementStyle
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * 自研选区控制器：聚合选区状态、坐标注册表与当前文档索引，
 * 把 overlay 的 window 坐标转成不依赖屏幕坐标的逻辑锚点 [SelectionAnchor]。
 *
 * 锚点链：window → block-local（[SelectionCoordinateRegistry.windowToBlockLocal]）
 * → 命中 run（[hitTestRunInBlock]）→ run 内字符偏移（[TextMeasurer]）→ +charStart。
 */
@Stable
internal class MarkdownSelectionController(
    private val coroutineScope: CoroutineScope,
    private val textMeasurer: TextMeasurer,
) {
    val state = MarkdownSelectionState()
    val registry = SelectionCoordinateRegistry()

    private var index: SelectionModelIndex = SelectionModelIndex(emptyList())
    private val incrementalIndexBuilder = IncrementalSelectionIndexBuilder()
    private val visibleLayoutBlocks = mutableMapOf<Long, InternalLayoutBlockModel>()
    private val geometryByStableId = mutableMapOf<Long, SelectionBlockGeometry>()
    private val textLayoutsByBlock = mutableMapOf<Long, MutableMap<VisibleTextLayoutKey, VisibleRunTextLayout>>()
    private var clipboard: Clipboard? = null
    private var startAnchor: SelectionAnchor? = null
    private var draggedHandleAnchor: SelectionAnchor? by mutableStateOf(null)
    private var fixedHandleAnchor: SelectionAnchor? = null
    private var skipNextTapClear: Boolean = false

    internal var fallbackTextMeasurementCount: Long = 0
        private set

    val hasSelection: Boolean get() = state.hasSelection

    fun bindClipboard(clipboard: Clipboard) {
        this.clipboard = clipboard
    }

    /** Refresh the lightweight whole-document index without laying out offscreen blocks. */
    fun updateDocument(blocks: List<InternalRenderBlockModel>) {
        replaceIndex(incrementalIndexBuilder.build(blocks))
    }

    /** Legacy/test entry point for callers that already own an eager layout tree. */
    fun updateIndex(blocks: List<InternalLayoutBlockModel>) {
        replaceIndex(buildSelectionIndexFromLayout(blocks))
        fun registerTree(block: InternalLayoutBlockModel) {
            registerVisibleBlock(block)
            when (block) {
                is com.hrm.markdown.renderer.internal.layout.model.LayoutListBlockModel ->
                    block.items.forEach { item -> item.children.forEach(::registerTree) }
                is com.hrm.markdown.renderer.internal.layout.model.LayoutColumnsBlockModel ->
                    block.columns.forEach { column -> column.children.forEach(::registerTree) }
                is com.hrm.markdown.renderer.internal.layout.model.LayoutTabBlockModel ->
                    block.tabs.forEach { tab -> tab.children.forEach(::registerTree) }
                is com.hrm.markdown.renderer.internal.layout.model.LayoutFootnoteBlockModel -> {
                    block.leadChild?.let(::registerTree)
                    block.trailingChildren.forEach(::registerTree)
                }
                is com.hrm.markdown.renderer.internal.layout.model.LayoutDefinitionListBlockModel ->
                    block.items.forEach { item ->
                        if (item is com.hrm.markdown.renderer.internal.layout.model.LayoutDefinitionDescriptionGroup) {
                            item.children.forEach(::registerTree)
                        }
                    }
                is com.hrm.markdown.renderer.internal.layout.model.LayoutRenderBlockModel ->
                    block.children.forEach(::registerTree)
                else -> Unit
            }
        }
        blocks.forEach(::registerTree)
    }

    fun registerVisibleBlock(block: InternalLayoutBlockModel) {
        val stableId = block.identity.stableId
        val previous = visibleLayoutBlocks.put(stableId, block)
        if (previous != null &&
            (previous.identity.contentRevision != block.identity.contentRevision ||
                previous.identity.layoutRevision != block.identity.layoutRevision)
        ) {
            textLayoutsByBlock.remove(stableId)
        }
        rebuildGeometry(stableId)
    }

    fun unregisterVisibleBlock(stableId: Long) {
        visibleLayoutBlocks.remove(stableId)
        geometryByStableId.remove(stableId)
        textLayoutsByBlock.remove(stableId)
    }

    /** Reuse the TextLayoutResult produced by BasicText instead of measuring selected runs again. */
    fun registerTextLayout(
        blockStableId: Long,
        placements: List<LayoutInlineRunPlacement>,
        result: TextLayoutResult,
    ) {
        if (visibleLayoutBlocks[blockStableId] == null) return
        val textPlacements = placements.filter { it.run is LayoutTextRun }
        val first = textPlacements.firstOrNull() ?: return
        val expectedText = buildString {
            textPlacements.forEach { placement ->
                append((placement.run as LayoutTextRun).text.text)
            }
        }
        if (result.layoutInput.text.text != expectedText) return
        val blockCache = textLayoutsByBlock.getOrPut(blockStableId) { mutableMapOf() }
        var textStart = 0
        for (placement in textPlacements) {
            val run = placement.run as LayoutTextRun
            blockCache[run.visibleTextLayoutKey()] = VisibleRunTextLayout(
                result = result,
                textStart = textStart,
                runX = (placement.x - first.x).toFloat(),
                runY = (placement.y - first.y).toFloat(),
            )
            textStart += run.text.length
        }
    }

    private fun replaceIndex(newIndex: SelectionModelIndex) {
        index = newIndex
        geometryByStableId.clear()
        visibleLayoutBlocks.keys.toList().forEach(::rebuildGeometry)
        val current = state.range ?: return
        val start = index.clampAnchor(current.start)
        val end = index.clampAnchor(current.end)
        state.range = if (start != null && end != null) index.normalize(start, end) else null
    }

    private fun rebuildGeometry(stableId: Long) {
        val block = visibleLayoutBlocks[stableId] ?: return
        val entry = index.entryOf(stableId) ?: return
        geometryByStableId[stableId] = buildSelectionGeometry(block, entry)
    }

    /** 返回某 block 内字符区间对应的 run 切片，供高亮绘制使用。 */
    fun runSlicesFor(stableId: Long): List<RunCharSlice> {
        val range = state.range ?: return emptyList()
        val entry = index.entryOf(stableId) ?: return emptyList()
        val geometry = geometryByStableId[stableId] ?: return emptyList()
        val order = entry.order
        if (order < (index.orderOf(range.start.blockStableId) ?: return emptyList())) return emptyList()
        if (order > (index.orderOf(range.end.blockStableId) ?: return emptyList())) return emptyList()

        val from = if (stableId == range.start.blockStableId) range.start.charInBlock else 0
        val to = if (stableId == range.end.blockStableId) range.end.charInBlock else entry.totalChars
        return runRangeForBlock(geometry, entry.totalChars, from, to)
    }

    /**
     * 返回某 block 内选区高亮矩形（block-local 坐标，原点为 block.frame 左上），
     * 坐标公式与 [PaintInlineLayoutContent] 放置一致：`left = run.frame.left - block.frame.left`。
     */
    fun highlightBoxesFor(stableId: Long): List<Rect> {
        val entry = index.entryOf(stableId) ?: return emptyList()
        val geometry = geometryByStableId[stableId] ?: return emptyList()
        val slices = runSlicesFor(stableId)
        if (slices.isEmpty()) {
            return if (geometry.runs.isEmpty()) wholeBlockHighlightBox(entry, geometry) else emptyList()
        }
        val block = geometry.block as? LayoutInlineBlockModel
            ?: return wholeBlockHighlightBox(entry, geometry)

        val boxes = ArrayList<Rect>(slices.size)
        for (slice in slices) {
            val run = slice.span.run
            val runLeft = run.frame.left - block.frame.left
            val runTop = run.frame.top - block.frame.top
            val text = slice.span.text
            val startX: Float
            val endX: Float
            if (run !is LayoutTextRun || text.isEmpty()) {
                startX = 0f
                endX = run.frame.width
            } else {
                val layout = textLayoutFor(geometry, slice.span) ?: continue
                val s = slice.startInRun.coerceIn(0, text.length)
                val e = slice.endInRun.coerceIn(0, text.length)
                startX = layout.horizontalPosition(s)
                endX = layout.horizontalPosition(e)
            }
            val left = runLeft + minOf(startX, endX)
            val right = runLeft + maxOf(startX, endX)
            boxes += Rect(left, runTop, right, runTop + run.frame.height)
        }
        return boxes
    }

    private fun wholeBlockHighlightBox(
        entry: SelectionBlockEntry,
        geometry: SelectionBlockGeometry,
    ): List<Rect> {
        val range = state.range ?: return emptyList()
        val order = entry.order
        if (order < (index.orderOf(range.start.blockStableId) ?: return emptyList())) return emptyList()
        if (order > (index.orderOf(range.end.blockStableId) ?: return emptyList())) return emptyList()

        val from = if (entry.stableId == range.start.blockStableId) range.start.charInBlock else 0
        val to = if (entry.stableId == range.end.blockStableId) range.end.charInBlock else entry.totalChars
        if (to <= from) return emptyList()

        val actualSize = registry.snapshotOf(entry.stableId)?.size
        val width = actualSize?.width?.toFloat() ?: geometry.block.frame.width
        val height = actualSize?.height?.toFloat() ?: geometry.block.frame.height
        return listOf(Rect(0f, 0f, width, height))
    }

    fun beginSelectionAt(windowOffset: Offset) {
        val anchor = anchorFromWindow(windowOffset) ?: return
        skipNextTapClear = false
        startAnchor = anchor
        draggedHandleAnchor = null
        fixedHandleAnchor = null
        state.activeHandle = SelectionActiveHandle.End
        state.isHandleDrag = false
        state.toolbarRequestKey = 0
        state.range = SelectionRange(anchor, anchor)
    }

    fun extendSelectionTo(windowOffset: Offset) {
        val start = startAnchor ?: return
        val moving = anchorFromWindow(windowOffset) ?: return
        state.range = index.normalize(start, moving)
    }

    fun finishSelectionGesture() {
        state.activeHandle = SelectionActiveHandle.None
        state.isHandleDrag = false
        draggedHandleAnchor = null
        fixedHandleAnchor = null
        if (selectedText.isEmpty()) {
            clearSelection()
        } else {
            skipNextTapClear = true
            state.toolbarRequestKey += 1
        }
    }

    /** 把根容器 local 坐标（来自 pointerInput）转成 window 坐标后开始选区。 */
    fun beginSelectionAtRootLocal(rootLocal: Offset) {
        val root = registry.rootCoordinates?.takeIf { it.isAttached } ?: return
        beginSelectionAt(root.localToWindow(rootLocal))
    }

    /** 把根容器 local 坐标（来自 pointerInput）转成 window 坐标后延展选区。 */
    fun extendSelectionToRootLocal(rootLocal: Offset) {
        val root = registry.rootCoordinates?.takeIf { it.isAttached } ?: return
        extendSelectionTo(root.localToWindow(rootLocal))
    }

    fun clearSelection() {
        skipNextTapClear = false
        startAnchor = null
        draggedHandleAnchor = null
        fixedHandleAnchor = null
        state.clear()
    }

    fun clearSelectionFromTap() {
        if (skipNextTapClear) {
            skipNextTapClear = false
            return
        }
        clearSelection()
    }

    fun selectAll() {
        val first = index.firstAnchor ?: return
        val last = index.lastAnchor ?: return
        startAnchor = first
        state.range = index.normalize(first, last)
    }

    fun beginSelectionHandleDrag(handle: SelectionActiveHandle) {
        if (handle == SelectionActiveHandle.None) return
        val range = state.range ?: return
        draggedHandleAnchor = when (handle) {
            SelectionActiveHandle.Start -> range.start
            SelectionActiveHandle.End -> range.end
            SelectionActiveHandle.None -> return
        }
        fixedHandleAnchor = when (handle) {
            SelectionActiveHandle.Start -> range.end
            SelectionActiveHandle.End -> range.start
            SelectionActiveHandle.None -> return
        }
        skipNextTapClear = false
        state.activeHandle = handle
        state.isHandleDrag = true
    }

    fun dragSelectionHandleToRootLocal(rootLocal: Offset) {
        if (!state.isHandleDrag) return
        val root = registry.rootCoordinates?.takeIf { it.isAttached } ?: return
        dragSelectionHandleTo(anchorFromWindow(root.localToWindow(rootLocal)) ?: return)
    }

    internal fun dragSelectionHandleTo(anchor: SelectionAnchor) {
        if (!state.isHandleDrag) return
        val fixed = fixedHandleAnchor ?: return
        val moving = index.clampAnchor(anchor) ?: return
        draggedHandleAnchor = moving
        state.range = index.normalize(fixed, moving)
    }

    fun finishSelectionHandleDrag() {
        if (!state.isHandleDrag) return
        state.activeHandle = SelectionActiveHandle.None
        state.isHandleDrag = false
        draggedHandleAnchor = null
        fixedHandleAnchor = null
        if (selectedText.isEmpty()) {
            clearSelection()
        } else {
            skipNextTapClear = false
            state.toolbarRequestKey += 1
        }
    }

    internal fun selectionAnchorForHandle(handle: SelectionActiveHandle): SelectionAnchor? {
        if (handle == SelectionActiveHandle.None) return null
        if (state.isHandleDrag) {
            return if (handle == state.activeHandle) draggedHandleAnchor else fixedHandleAnchor
        }
        val range = state.range ?: return null
        return when (handle) {
            SelectionActiveHandle.Start -> range.start
            SelectionActiveHandle.End -> range.end
            SelectionActiveHandle.None -> null
        }
    }

    fun selectionHandlePositionInRoot(handle: SelectionActiveHandle): SelectionHandlePosition? {
        val anchor = selectionAnchorForHandle(handle) ?: return null
        val entry = index.entryOf(anchor.blockStableId) ?: return null
        val geometry = geometryByStableId[anchor.blockStableId] ?: return null
        val blockSnapshot = registry.snapshotOf(anchor.blockStableId) ?: return null
        val rootSnapshot = registry.rootSnapshot ?: return null
        val blockCoordinates = registry.coordinatesOf(anchor.blockStableId) ?: return null
        val rootCoordinates = registry.rootCoordinates?.takeIf { it.isAttached } ?: return null
        val inlineBlock = geometry.block as? LayoutInlineBlockModel
        val blockLocalPosition: Offset
        if (inlineBlock == null || geometry.runs.isEmpty()) {
            val atStart = anchor.charInBlock <= 0
            blockLocalPosition = Offset(
                x = if (atStart) 0f else blockSnapshot.size.width.toFloat(),
                y = if (atStart) 0f else blockSnapshot.size.height.toFloat(),
            )
        } else {
            val (span, offsetInRun) = charToRunForHandle(
                geometry = geometry,
                totalChars = entry.totalChars,
                charInBlock = anchor.charInBlock,
                handle = handle,
            )
                ?: return null
            val run = span.run
            val runLeft = run.frame.left - inlineBlock.frame.left
            val runTop = run.frame.top - inlineBlock.frame.top
            val horizontal = textLayoutFor(geometry, span)?.horizontalPosition(
                offsetInRun.coerceIn(0, span.text.length)
            ) ?: if (offsetInRun <= 0) 0f else run.frame.width
            blockLocalPosition = Offset(
                x = runLeft + horizontal,
                y = runTop + run.frame.height,
            )
        }
        val positionInWindow = blockCoordinates.localToWindow(blockLocalPosition)
        val positionInRoot = rootCoordinates.windowToLocal(positionInWindow)
        if (positionInRoot.x < 0f || positionInRoot.x > rootSnapshot.size.width ||
            positionInRoot.y < 0f || positionInRoot.y > rootSnapshot.size.height
        ) {
            return null
        }
        return SelectionHandlePosition(positionInRoot)
    }

    val selectedText: String
        get() = state.range?.let { extractSelectedText(index, it) } ?: ""

    fun copySelection() {
        val text = selectedText
        if (text.isEmpty()) return
        val target = clipboard ?: return
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            target.setClipEntry(plainTextClipEntry(text))
        }
    }

    /**
     * 当前选区在 window 坐标系下的并集包围盒，供 [androidx.compose.ui.platform.TextToolbar.showMenu]
     * 定位浮动菜单使用。遍历所有可见且参与选区的 block，把其 block-local 高亮矩形经
     * [androidx.compose.ui.layout.LayoutCoordinates.localToWindow] 映射后求并集；无可见选区时返回 null。
     */
    fun selectionBoundsInWindow(): Rect? {
        if (state.range == null) return null
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        var found = false
        for (entry in registry.visibleEntries(index)) {
            if (geometryByStableId[entry.stableId] == null) continue
            val coords = registry.coordinatesOf(entry.stableId) ?: continue
            val boxes = highlightBoxesFor(entry.stableId)
            for (box in boxes) {
                val topLeft = coords.localToWindow(Offset(box.left, box.top))
                val bottomRight = coords.localToWindow(Offset(box.right, box.bottom))
                left = minOf(left, topLeft.x, bottomRight.x)
                top = minOf(top, topLeft.y, bottomRight.y)
                right = maxOf(right, topLeft.x, bottomRight.x)
                bottom = maxOf(bottom, topLeft.y, bottomRight.y)
                found = true
            }
        }
        if (!found) return null
        return Rect(left, top, right, bottom)
    }

    /**
     * 把 window 坐标解析为逻辑锚点：
     * 选择纵向最接近的可见 block，命中其 run，再用与布局一致的测量口径求字符偏移。
     */
    private fun anchorFromWindow(windowOffset: Offset): SelectionAnchor? {
        val visible = registry.visibleEntries(index)
        if (visible.isEmpty()) return null

        var best: SelectionBlockEntry? = null
        var bestLocal: Offset = Offset.Zero
        var bestPenalty = Float.MAX_VALUE
        for (entry in visible) {
            if (geometryByStableId[entry.stableId] == null) continue
            val coords = registry.coordinatesOf(entry.stableId) ?: continue
            val local = coords.windowToLocal(windowOffset)
            val height = coords.size.height.toFloat()
            val penalty = when {
                local.y < 0f -> -local.y
                local.y > height -> local.y - height
                else -> 0f
            }
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                best = entry
                bestLocal = local
            }
            if (penalty == 0f) {
                // Inside this block vertically; prefer it directly.
                best = entry
                bestLocal = local
                break
            }
        }

        val entry = best ?: return null
        val geometry = geometryByStableId[entry.stableId] ?: return null
        val inlineBlock = geometry.block as? LayoutInlineBlockModel
        if (inlineBlock == null) {
            return SelectionAnchor(entry.stableId, textOnlyBlockOffset(entry, geometry, bestLocal))
        }
        val hit = hitTestRunInBlock(inlineBlock, bestLocal.x, bestLocal.y) ?: run {
            // No runs at all; snap to block boundary by horizontal side.
            return SelectionAnchor(entry.stableId, if (bestLocal.x <= 0f) 0 else entry.totalChars)
        }

        val span = geometry.runs.firstOrNull {
            it.lineIndex == hit.lineIndex && it.runIndex == hit.runIndex
        } ?: return SelectionAnchor(entry.stableId, 0)

        val offsetInRun = charOffsetInRun(geometry, span, hit)
        val charInBlock = (span.charStart + offsetInRun).coerceIn(0, entry.totalChars)
        return SelectionAnchor(entry.stableId, charInBlock)
    }

    private fun textOnlyBlockOffset(
        entry: SelectionBlockEntry,
        geometry: SelectionBlockGeometry,
        local: Offset,
    ): Int {
        if (entry.totalChars == 0) return 0
        val height = (
            registry.snapshotOf(entry.stableId)?.size?.height?.toFloat()
                ?: geometry.block.frame.height
            ).coerceAtLeast(1f)
        return if (local.y < height / 2f) 0 else entry.totalChars
    }

    private fun charOffsetInRun(
        geometry: SelectionBlockGeometry,
        span: SelectionRunSpan,
        hit: RunHit,
    ): Int {
        val text = hit.run.text
        if (text.isEmpty()) return 0
        val layout = textLayoutFor(geometry, span) ?: return 0
        val raw = layout.offsetForPosition(
            x = hit.runLocalX,
            y = hit.runLocalY.coerceAtLeast(0f),
        )
        return clampToCharBoundary(text, raw.coerceIn(0, text.length))
    }

    private fun charToRunForHandle(
        geometry: SelectionBlockGeometry,
        totalChars: Int,
        charInBlock: Int,
        handle: SelectionActiveHandle,
    ): Pair<SelectionRunSpan, Int>? {
        if (handle != SelectionActiveHandle.End || charInBlock <= 0) {
            return geometry.charToRun(charInBlock, totalChars)
        }
        val clamped = charInBlock.coerceIn(0, totalChars)
        val span = geometry.runs.firstOrNull {
            clamped > it.charStart && clamped <= it.charEnd
        } ?: return geometry.charToRun(clamped, totalChars)
        return span to (clamped - span.charStart)
    }

    private fun SelectionBlockGeometry.charToRun(
        charInBlock: Int,
        totalChars: Int,
    ): Pair<SelectionRunSpan, Int>? {
        if (runs.isEmpty()) return null
        val clamped = charInBlock.coerceIn(0, totalChars)
        for (span in runs) {
            if (clamped < span.charEnd) {
                return span to (clamped - span.charStart).coerceIn(0, span.text.length)
            }
        }
        val last = runs.last()
        return last to last.text.length
    }

    private fun textLayoutFor(
        geometry: SelectionBlockGeometry,
        span: SelectionRunSpan,
    ): VisibleRunTextLayout? {
        val block = geometry.block as? LayoutInlineBlockModel ?: return null
        val run = span.run as? LayoutTextRun ?: return null
        val key = run.visibleTextLayoutKey()
        val blockCache = textLayoutsByBlock.getOrPut(geometry.stableId) { mutableMapOf() }
        return blockCache.getOrPut(key) {
            fallbackTextMeasurementCount++
            VisibleRunTextLayout(
                result = textMeasurer.measure(
                    text = run.text,
                    style = textMeasurementStyle(block.style),
                    constraints = Constraints(maxWidth = Int.MAX_VALUE),
                    maxLines = 1,
                    softWrap = false,
                ),
            )
        }
    }
}

private fun LayoutTextRun.visibleTextLayoutKey(): VisibleTextLayoutKey =
    VisibleTextLayoutKey(
        stableId = identity.stableId,
        contentRevision = identity.contentRevision,
        text = text.text,
    )

private data class VisibleRunTextLayout(
    val result: TextLayoutResult,
    val textStart: Int = 0,
    val runX: Float = 0f,
    val runY: Float = 0f,
) {
    fun horizontalPosition(offsetInRun: Int): Float =
        result.getHorizontalPosition(
            offset = (textStart + offsetInRun).coerceIn(0, result.layoutInput.text.length),
            usePrimaryDirection = true,
        ) - runX

    fun offsetForPosition(x: Float, y: Float): Int =
        result.getOffsetForPosition(Offset(x + runX, y + runY)) - textStart
}

private data class VisibleTextLayoutKey(
    val stableId: Long,
    val contentRevision: Long,
    val text: String,
)

internal data class SelectionHandlePosition(
    val positionInRoot: Offset,
)

internal val LocalMarkdownSelectionController =
    staticCompositionLocalOf<MarkdownSelectionController?> { null }

@Composable
internal fun rememberMarkdownSelectionController(
    coroutineScope: CoroutineScope,
    textMeasurer: TextMeasurer,
): MarkdownSelectionController {
    return remember(coroutineScope, textMeasurer) {
        MarkdownSelectionController(coroutineScope, textMeasurer)
    }
}
