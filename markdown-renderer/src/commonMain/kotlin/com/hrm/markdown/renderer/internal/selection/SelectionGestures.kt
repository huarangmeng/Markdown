package com.hrm.markdown.renderer.internal.selection

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import com.hrm.markdown.renderer.MarkdownSelectionActions
import com.hrm.markdown.renderer.MarkdownSelectionInfo
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect
import com.hrm.markdown.renderer.LocalMarkdownStyle

/**
 * 把自研选区层的交互手势挂到根容器：
 * - 长按拖拽进入并延展选区（[detectDragGesturesAfterLongPress]）；
 * - 点击空白处清除选区（[detectTapGestures]）。
 *
 * 手势节点与 [SelectionCoordinateRegistry.setRoot] 处于同一根 Box，
 * 因此 pointer 的 local 坐标即根容器坐标，交给控制器换算成 window → 逻辑锚点。
 */
internal fun Modifier.markdownSelectionGestures(
    controller: MarkdownSelectionController,
): Modifier =
    this
        .pointerInput(controller) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> controller.beginSelectionAtRootLocal(offset) },
                onDragEnd = { controller.finishSelectionGesture() },
                onDragCancel = { controller.finishSelectionGesture() },
                onDrag = { change, _ ->
                    controller.extendSelectionToRootLocal(change.position)
                    change.consume()
                },
            )
        }
        .pointerInput(controller) {
            detectTapGestures(
                onPress = {
                    val selectionAtPress = controller.state.range
                    // An unmoved long press also releases this detector; only an independent tap clears selection.
                    if (tryAwaitRelease() && controller.state.range === selectionAtPress) {
                        controller.clearSelection()
                    }
                },
            )
        }

/**
 * 监听选区变化并弹出工具栏：
 * - 宿主提供了自定义工具栏（[MarkdownComponents.selectionToolbar]）时，以
 *   [MarkdownSelectionInfo]（含文本、窗口包围盒、逐块区间）重组它，由宿主自行定位、
 *   呈现与消失（[MarkdownSelectionActions]）；
 * - 否则复用平台 [androidx.compose.ui.platform.TextToolbar]：
 *   有选区时按其 window 包围盒弹出系统浮动菜单（复制/翻译/分享等系统项随平台提供），
 *   选区清空时隐藏菜单。复制后立即清除选区并隐藏。
 */
@Composable
internal fun SelectionToolbarHost(
    controller: MarkdownSelectionController,
    selectionToolbar: (@Composable (MarkdownSelectionInfo, MarkdownSelectionActions) -> Unit)?,
) {
    if (selectionToolbar != null) {
        CustomSelectionToolbarHost(controller, selectionToolbar)
        return
    }
    val textToolbar = LocalTextToolbar.current
    val range = controller.state.range
    val toolbarRequestKey = controller.state.toolbarRequestKey
    val activeHandle = controller.state.activeHandle
    var watchExternalDismiss by remember(controller) { mutableStateOf(false) }
    LaunchedEffect(range, toolbarRequestKey, activeHandle) {
        if (range == null || activeHandle != SelectionActiveHandle.None) {
            watchExternalDismiss = false
            textToolbar.hide()
        } else if (toolbarRequestKey > 0) {
            val rect = controller.selectionBoundsInWindow() ?: return@LaunchedEffect
            textToolbar.showMenu(
                rect = rect,
                onCopyRequested = {
                    controller.copySelection()
                    controller.clearSelection()
                },
                onSelectAllRequested = { controller.selectAll() },
            )
            watchExternalDismiss = true
        }
    }
    LaunchedEffect(textToolbar, range, activeHandle, watchExternalDismiss) {
        if (range == null || activeHandle != SelectionActiveHandle.None || !watchExternalDismiss) {
            return@LaunchedEffect
        }
        var seenShown = textToolbar.status == TextToolbarStatus.Shown
        snapshotFlow { textToolbar.status }.collect { status ->
            when (status) {
                TextToolbarStatus.Shown -> seenShown = true
                TextToolbarStatus.Hidden -> {
                    if (seenShown &&
                        controller.state.range != null &&
                        controller.state.activeHandle == SelectionActiveHandle.None
                    ) {
                        watchExternalDismiss = false
                        controller.clearSelection()
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomSelectionToolbarHost(
    controller: MarkdownSelectionController,
    selectionToolbar: @Composable (MarkdownSelectionInfo, MarkdownSelectionActions) -> Unit,
) {
    // selectionInfo() 读取选区状态，选区变化时随之重组；无选区时不渲染。
    val info = controller.selectionInfo()
    if (info == null) return
    val actions = remember(controller) {
        MarkdownSelectionActions(
            onCopy = {
                controller.copySelection()
                controller.clearSelection()
            },
            onSelectAll = { controller.selectAll() },
            onDismiss = { controller.clearSelection() },
        )
    }
    selectionToolbar(info, actions)
}
