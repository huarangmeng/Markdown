package com.hrm.markdown.renderer.internal.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.hrm.markdown.renderer.internal.layout.inline.textMeasurementStyle
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutBlockModel
import kotlinx.coroutines.CoroutineScope

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
    private var clipboard: ClipboardManager? = null
    private var startAnchor: SelectionAnchor? = null

    val hasSelection: Boolean get() = state.hasSelection

    fun bindClipboard(clipboard: ClipboardManager) {
        this.clipboard = clipboard
    }

    /** 文档（重）布局后刷新索引，并把既有选区夹紧到新索引（reflow 容错）。 */
    fun updateIndex(blocks: List<InternalLayoutBlockModel>) {
        index = buildSelectionIndex(blocks)
        val current = state.range ?: return
        val start = index.clampAnchor(current.start)
        val end = index.clampAnchor(current.end)
        state.range = if (start != null && end != null) index.normalize(start, end) else null
    }

    /** 返回某 block 内字符区间对应的 run 切片，供高亮绘制使用。 */
    fun runSlicesFor(stableId: Long): List<RunCharSlice> {
        val range = state.range ?: return emptyList()
        val entry = index.entryOf(stableId) ?: return emptyList()
        val order = entry.order
        if (order < (index.orderOf(range.start.blockStableId) ?: return emptyList())) return emptyList()
        if (order > (index.orderOf(range.end.blockStableId) ?: return emptyList())) return emptyList()

        val from = if (stableId == range.start.blockStableId) range.start.charInBlock else 0
        val to = if (stableId == range.end.blockStableId) range.end.charInBlock else entry.totalChars
        return runRangeForBlock(entry, from, to)
    }

    /**
     * 返回某 block 内选区高亮矩形（block-local 坐标，原点为 block.frame 左上），
     * 坐标公式与 [PaintInlineLayoutContent] 放置一致：`left = run.frame.left - block.frame.left`。
     */
    fun highlightBoxesFor(stableId: Long): List<Rect> {
        val slices = runSlicesFor(stableId)
        if (slices.isEmpty()) return emptyList()
        val entry = index.entryOf(stableId) ?: return emptyList()
        val block = entry.block
        val style = textMeasurementStyle(block.style)

        val boxes = ArrayList<Rect>(slices.size)
        for (slice in slices) {
            val run = slice.span.run
            val runLeft = run.frame.left - block.frame.left
            val runTop = run.frame.top - block.frame.top
            val text = run.text
            val startX: Float
            val endX: Float
            if (text.isEmpty()) {
                startX = 0f
                endX = run.frame.width
            } else {
                val layout = textMeasurer.measure(
                    text = text,
                    style = style,
                    constraints = Constraints(maxWidth = Int.MAX_VALUE),
                    maxLines = 1,
                    softWrap = false,
                )
                val s = slice.startInRun.coerceIn(0, text.length)
                val e = slice.endInRun.coerceIn(0, text.length)
                startX = layout.getHorizontalPosition(s, usePrimaryDirection = true)
                endX = layout.getHorizontalPosition(e, usePrimaryDirection = true)
            }
            val left = runLeft + minOf(startX, endX)
            val right = runLeft + maxOf(startX, endX)
            boxes += Rect(left, runTop, right, runTop + run.frame.height)
        }
        return boxes
    }

    fun beginSelectionAt(windowOffset: Offset) {
        val anchor = anchorFromWindow(windowOffset) ?: return
        startAnchor = anchor
        state.activeHandle = SelectionActiveHandle.End
        state.range = SelectionRange(anchor, anchor)
    }

    fun extendSelectionTo(windowOffset: Offset) {
        val start = startAnchor ?: return
        val moving = anchorFromWindow(windowOffset) ?: return
        state.range = index.normalize(start, moving)
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
        startAnchor = null
        state.clear()
    }

    fun selectAll() {
        val first = index.firstAnchor ?: return
        val last = index.lastAnchor ?: return
        startAnchor = first
        state.range = index.normalize(first, last)
    }

    val selectedText: String
        get() = state.range?.let { extractSelectedText(index, it) } ?: ""

    fun copySelection() {
        val text = selectedText
        if (text.isEmpty()) return
        val target = clipboard ?: return
        target.setText(AnnotatedString(text))
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
        val hit = hitTestRunInBlock(entry.block, bestLocal.x, bestLocal.y) ?: run {
            // No runs at all; snap to block boundary by horizontal side.
            return SelectionAnchor(entry.stableId, if (bestLocal.x <= 0f) 0 else entry.totalChars)
        }

        val span = entry.runs.firstOrNull {
            it.lineIndex == hit.lineIndex && it.runIndex == hit.runIndex
        } ?: return SelectionAnchor(entry.stableId, 0)

        val offsetInRun = charOffsetInRun(entry.block.style, hit)
        val charInBlock = (span.charStart + offsetInRun).coerceIn(0, entry.totalChars)
        return SelectionAnchor(entry.stableId, charInBlock)
    }

    private fun charOffsetInRun(style: TextStyle, hit: RunHit): Int {
        val text = hit.run.text
        if (text.isEmpty()) return 0
        val layout = textMeasurer.measure(
            text = text,
            style = textMeasurementStyle(style),
            constraints = Constraints(maxWidth = Int.MAX_VALUE),
            maxLines = 1,
            softWrap = false,
        )
        val raw = layout.getOffsetForPosition(Offset(hit.runLocalX, hit.runLocalY.coerceAtLeast(0f)))
        return clampToCharBoundary(text, raw.coerceIn(0, text.length))
    }
}

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
