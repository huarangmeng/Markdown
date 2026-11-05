package com.hrm.markdown.renderer.internal.selection

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.inline.InlinePlaceholderId
import com.hrm.markdown.renderer.internal.core.model.ImageWidgetModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutWidgetRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SelectionGesturesTest {
    @Test
    fun should_leaveRootSelectionUntouched_when_childConsumesLongPress() = runComposeUiTest {
        val fixture = SelectionGestureFixture(consumeChildLongPress = true)
        setContent { fixture.Content() }
        waitForIdle()
        onNodeWithTag(SelectionRootTag).performTouchInput {
            down(Offset(230f, 10f))
            advanceEventTime(1000L)
            up()
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1, fixture.childLongPresses)
            assertFalse(fixture.controller.hasSelection)
            assertTrue(fixture.controller.selectRange(1, 2, 8))
        }
        val selection = runOnIdle { fixture.controller.state.range }
        onNodeWithTag(SelectionRootTag).performTouchInput {
            // Stay inside the child but outside the selected range's handle popups.
            down(Offset(230f, 10f))
            advanceEventTime(1000L)
            up()
        }
        waitForIdle()
        runOnIdle {
            assertEquals(2, fixture.childLongPresses)
            assertEquals(selection, fixture.controller.state.range)
        }
    }

    @Test
    fun should_clearSelectionOnFirstTap_when_longPressDragHasFinished() = runComposeUiTest {
        val fixture = SelectionGestureFixture()
        setContent { fixture.Content() }
        waitForIdle()

        // A new selection must not inherit click suppression from a previous gesture either.
        repeat(2) {
            onNodeWithTag(SelectionRootTag).performTouchInput { selectText() }
            waitForIdle()
            runOnIdle {
                assertTrue(fixture.controller.hasSelection, "Releasing the selection gesture must keep the selection")
                assertEquals(SelectionActiveHandle.None, fixture.controller.state.activeHandle)
                assertTrue(fixture.controller.state.toolbarRequestKey > 0)
            }

            onNodeWithTag(SelectionRootTag).performTouchInput { click(Offset(width - 10f, height - 10f)) }
            waitForIdle()
            runOnIdle { assertFalse(fixture.controller.hasSelection, "The first independent tap must clear the selection") }
        }
    }

    @Test
    fun should_clearSelectionOnFirstTap_when_selectionHandleHasMoved() = runComposeUiTest {
        val fixture = SelectionGestureFixture()
        setContent { fixture.Content() }
        waitForIdle()
        onNodeWithTag(SelectionRootTag).performTouchInput { selectText() }
        waitForIdle()
        val initialRange = runOnIdle { fixture.controller.state.range }

        onNodeWithContentDescription(SelectionStartHandleDescription).performTouchInput {
            swipe(start = center, end = center + Offset(30f, 0f), durationMillis = 200L)
        }
        waitForIdle()
        runOnIdle {
            assertTrue(fixture.controller.hasSelection)
            assertNotEquals(initialRange, fixture.controller.state.range)
            assertFalse(fixture.controller.state.isHandleDrag)
        }

        onNodeWithTag(SelectionRootTag).performTouchInput { click(Offset(width - 10f, height - 10f)) }
        waitForIdle()
        runOnIdle { assertFalse(fixture.controller.hasSelection) }
    }

    @Test
    fun should_clearSelectionOnFirstTap_when_longPressDragIsCancelled() = runComposeUiTest {
        val fixture = SelectionGestureFixture()
        setContent { fixture.Content() }
        waitForIdle()
        onNodeWithTag(SelectionRootTag).performTouchInput {
            down(Offset(2f, 10f))
            advanceEventTime(1000L)
            moveTo(Offset(180f, 10f), delayMillis = 100L)
        }
        waitForIdle()
        runOnIdle {
            assertTrue(fixture.controller.hasSelection)
            fixture.consumeDrag = true
        }
        // Another recognizer consumes movement, cancelling this drag while the pointer is down.
        onNodeWithTag(SelectionRootTag).performTouchInput {
            moveTo(Offset(185f, 10f), delayMillis = 100L)
            up()
        }
        waitForIdle()
        runOnIdle {
            fixture.consumeDrag = false
            assertTrue(fixture.controller.hasSelection)
            assertEquals(SelectionActiveHandle.None, fixture.controller.state.activeHandle)
        }

        onNodeWithTag(SelectionRootTag).performTouchInput { click(Offset(width - 10f, height - 10f)) }
        waitForIdle()
        runOnIdle { assertFalse(fixture.controller.hasSelection) }
    }
}

private const val SelectionRootTag = "selection-root"

private fun TouchInjectionScope.selectText() {
    down(Offset(2f, 10f))
    advanceEventTime(1000L)
    moveTo(Offset(180f, 10f), delayMillis = 100L)
    up()
}

private class SelectionGestureFixture(private val consumeChildLongPress: Boolean = false) {
    private val block = inlineTextBlock(id = 1, text = "abcdefghijklmnopqrstuvwxyz", width = 260f, height = 30f).let { textBlock ->
        if (!consumeChildLongPress) textBlock else {
            val line = textBlock.lines.single()
            val textRun = line.runs.single() as com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
            val image = ImageWidgetModel(selIdentity(1002), "image", "", null, null, null)
            textBlock.copy(lines = listOf(line.copy(runs = listOf(
                textRun.copy(frame = textRun.frame.copy(width = 180f)),
                LayoutWidgetRun(
                    identity = image.identity,
                    frame = selRect(left = 200f, width = 60f, height = 30f),
                    id = InlinePlaceholderId.from(image),
                    widget = image,
                ),
            ))))
        }
    }
    lateinit var controller: MarkdownSelectionController
    var consumeDrag = false
    var childLongPresses = 0

    @Composable
    fun Content() {
        val current = rememberMarkdownSelectionController(rememberCoroutineScope(), rememberTextMeasurer())
        controller = current
        LaunchedEffect(current) { current.updateIndex(listOf(block)) }
        Box(
            Modifier
                .size(width = 320.dp, height = 120.dp)
                .testTag(SelectionRootTag)
                .onGloballyPositioned(current.registry::setRoot)
                .markdownSelectionGestures(current)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (consumeDrag) {
                                event.changes.forEach { change ->
                                    if (change.pressed && change.previousPressed) change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            Box(
                Modifier
                    .size(width = 260.dp, height = 30.dp)
                    .onGloballyPositioned { current.registry.register(block.identity.stableId, it) }
                    .pointerInput(consumeChildLongPress) {
                        if (consumeChildLongPress) detectTapGestures(onLongPress = { childLongPresses++ })
                    }
            )
            SelectionHandlesHost(current)
        }
    }
}
