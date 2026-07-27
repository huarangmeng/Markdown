package com.hrm.markdown.renderer.internal.selection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class SelectionHandlesTest {
    @Test
    fun should_show_start_and_end_handles_for_non_empty_selection() = runComposeUiTest {
        val block = inlineTextBlock(id = 1, text = "selectable text", width = 260f, height = 20f)
        var controllerRef: MarkdownSelectionController? = null

        setContent {
            val selectionController = rememberMarkdownSelectionController(
                coroutineScope = rememberCoroutineScope(),
                textMeasurer = rememberTextMeasurer(),
            )
            LaunchedEffect(selectionController) {
                selectionController.updateIndex(listOf(block))
                selectionController.state.range = SelectionRange(
                    start = SelectionAnchor(blockStableId = 1, charInBlock = 1),
                    end = SelectionAnchor(blockStableId = 1, charInBlock = 8),
                )
                controllerRef = selectionController
            }
            Box(
                modifier = Modifier
                    .size(width = 300.dp, height = 80.dp)
                    .onGloballyPositioned(selectionController.registry::setRoot)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 260.dp, height = 20.dp)
                        .onGloballyPositioned {
                            selectionController.registry.register(block.identity.stableId, it)
                        }
                )
                SelectionHandlesHost(selectionController)
            }
        }

        waitForIdle()

        onNodeWithContentDescription(SelectionStartHandleDescription).assertExists()
        onNodeWithContentDescription(SelectionEndHandleDescription).assertExists()

        val initialRange = requireNotNull(controllerRef).state.range
        onNodeWithContentDescription(SelectionStartHandleDescription).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(30f, 0f),
                durationMillis = 200L,
            )
        }
        waitForIdle()

        runOnIdle {
            val actual = requireNotNull(controllerRef)
            assertNotEquals(initialRange, actual.state.range)
            assertFalse(actual.state.isHandleDrag)
            assertEquals(SelectionActiveHandle.None, actual.state.activeHandle)
        }
    }

    @Test
    fun should_move_handle_with_selection_highlight_during_drag() = runComposeUiTest {
        val block = inlineTextBlock(id = 1, text = "selectable text", width = 260f, height = 20f)
        var controllerRef: MarkdownSelectionController? = null

        setContent {
            val controller = rememberMarkdownSelectionController(
                coroutineScope = rememberCoroutineScope(),
                textMeasurer = rememberTextMeasurer(),
            )
            LaunchedEffect(controller) {
                controller.updateIndex(listOf(block))
                controller.state.range = SelectionRange(
                    start = SelectionAnchor(blockStableId = 1, charInBlock = 1),
                    end = SelectionAnchor(blockStableId = 1, charInBlock = 8),
                )
                controllerRef = controller
            }
            Box(
                modifier = Modifier
                    .size(width = 300.dp, height = 80.dp)
                    .onGloballyPositioned(controller.registry::setRoot)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 260.dp, height = 20.dp)
                        .onGloballyPositioned {
                            controller.registry.register(block.identity.stableId, it)
                        }
                )
                SelectionHandlesHost(controller)
            }
        }

        waitForIdle()
        val initialRange = requireNotNull(controllerRef).state.range
        val initialLeft = onNodeWithContentDescription(SelectionStartHandleDescription)
            .fetchSemanticsNode()
            .boundsInRoot
            .left

        onNodeWithContentDescription(SelectionStartHandleDescription).performTouchInput {
            down(center)
            moveBy(Offset(30f, 0f))
            advanceEventTime(32L)
        }
        waitForIdle()

        assertNotEquals(initialRange, requireNotNull(controllerRef).state.range)
        val draggedLeft = onNodeWithContentDescription(SelectionStartHandleDescription)
            .fetchSemanticsNode()
            .boundsInRoot
            .left
        assertNotEquals(initialLeft, draggedLeft)

        onNodeWithContentDescription(SelectionStartHandleDescription).performTouchInput { up() }
        waitForIdle()
    }

    @Test
    fun should_keep_dragged_physical_handle_when_crossing_other_endpoint() = runComposeUiTest {
        val block = inlineTextBlock(id = 1, text = "abcdef", width = 120f, height = 20f)
        var controller: MarkdownSelectionController? = null

        setContent {
            val current = rememberMarkdownSelectionController(
                coroutineScope = rememberCoroutineScope(),
                textMeasurer = rememberTextMeasurer(),
            )
            LaunchedEffect(current) {
                current.updateIndex(listOf(block))
                current.state.range = SelectionRange(
                    start = SelectionAnchor(blockStableId = 1, charInBlock = 1),
                    end = SelectionAnchor(blockStableId = 1, charInBlock = 4),
                )
                controller = current
            }
        }

        waitForIdle()
        runOnIdle {
            val actual = requireNotNull(controller)
            actual.beginSelectionHandleDrag(SelectionActiveHandle.Start)
            actual.dragSelectionHandleTo(SelectionAnchor(blockStableId = 1, charInBlock = 5))

            assertEquals(
                SelectionRange(
                    start = SelectionAnchor(blockStableId = 1, charInBlock = 4),
                    end = SelectionAnchor(blockStableId = 1, charInBlock = 5),
                ),
                actual.state.range,
            )
            assertEquals(
                SelectionAnchor(blockStableId = 1, charInBlock = 5),
                actual.selectionAnchorForHandle(SelectionActiveHandle.Start),
            )
            assertEquals(
                SelectionAnchor(blockStableId = 1, charInBlock = 4),
                actual.selectionAnchorForHandle(SelectionActiveHandle.End),
            )

            actual.finishSelectionHandleDrag()
            assertFalse(actual.state.isHandleDrag)
            assertEquals(SelectionActiveHandle.None, actual.state.activeHandle)
            assertEquals(
                SelectionAnchor(blockStableId = 1, charInBlock = 4),
                actual.selectionAnchorForHandle(SelectionActiveHandle.Start),
            )
        }
    }

    @Test
    fun should_place_end_handle_on_previous_line_at_run_boundary() = runComposeUiTest {
        val block = inlineMultiRunBlock(
            id = 1,
            runs = listOf("abc", "def"),
            lineHeight = 20f,
            runWidth = 120f,
        )
        var controllerRef: MarkdownSelectionController? = null

        setContent {
            val controller = rememberMarkdownSelectionController(
                coroutineScope = rememberCoroutineScope(),
                textMeasurer = rememberTextMeasurer(),
            )
            LaunchedEffect(controller) {
                controller.updateIndex(listOf(block))
                controller.state.range = SelectionRange(
                    start = SelectionAnchor(blockStableId = 1, charInBlock = 0),
                    end = SelectionAnchor(blockStableId = 1, charInBlock = 3),
                )
                controllerRef = controller
            }
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 80.dp)
                    .onGloballyPositioned(controller.registry::setRoot)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 40.dp)
                        .onGloballyPositioned {
                            controller.registry.register(block.identity.stableId, it)
                        }
                )
                SelectionHandlesHost(controller)
            }
        }

        waitForIdle()
        runOnIdle {
            val position = assertNotNull(
                requireNotNull(controllerRef)
                    .selectionHandlePositionInRoot(SelectionActiveHandle.End)
            )
            assertEquals(expected = 20f, actual = position.positionInRoot.y, absoluteTolerance = 0.01f)
        }
    }
}
