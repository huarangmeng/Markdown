package com.hrm.markdown.renderer.internal.selection

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalTextToolbar

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
                onDrag = { change, _ ->
                    controller.extendSelectionToRootLocal(change.position)
                    change.consume()
                },
            )
        }
        .pointerInput(controller) {
            detectTapGestures(
                onTap = { controller.clearSelection() },
            )
        }

/**
 * 监听选区变化并复用平台 [androidx.compose.ui.platform.TextToolbar]：
 * 有选区时按其 window 包围盒弹出系统浮动菜单（复制/翻译/分享等系统项随平台提供），
 * 选区清空时隐藏菜单。复制后立即清除选区并隐藏。
 */
@Composable
internal fun SelectionToolbarHost(controller: MarkdownSelectionController) {
    val textToolbar = LocalTextToolbar.current
    val range = controller.state.range
    LaunchedEffect(range) {
        if (range == null) {
            textToolbar.hide()
        } else {
            val rect = controller.selectionBoundsInWindow() ?: return@LaunchedEffect
            textToolbar.showMenu(
                rect = rect,
                onCopyRequested = {
                    controller.copySelection()
                    controller.clearSelection()
                },
                onSelectAllRequested = { controller.selectAll() },
            )
        }
    }
}
