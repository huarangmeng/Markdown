package com.hrm.markdown.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.internal.selection.LocalMarkdownSelectionController
import com.hrm.markdown.renderer.internal.selection.MarkdownSelectionController
import com.hrm.markdown.renderer.internal.selection.SelectionActiveHandle
import com.hrm.markdown.renderer.internal.selection.SelectionAnchor
import java.awt.datatransfer.DataFlavor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownSelectionLifecycleTest {
    /** 原地长按真实字形必须留下完整词选区，不能要求用户额外拖动才允许复制。 */
    @Test
    fun should_selectAndCopyPaintedWord_when_longPressIsReleasedWithoutDragging() = runComposeUiTest {
        val page = SelectionPage("before beta after")
        setContent { page.Content() }
        for ((source, expected) in listOf(
            "before beta after" to "beta",
            "before ==highlight== after" to "highlight",
            "before 😀 after" to "😀",
        )) {
            runOnIdle { page.source = source }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasText(expected, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            val text = onAllNodes(
                hasText(expected, substring = true) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            )[0]
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            val layout = layouts.single()
            val glyph = layout.getBoundingBox(layout.layoutInput.text.text.indexOf(expected))
            text.performTouchInput { longClick(glyph.center) }
            waitForIdle()
            runOnIdle {
                assertEquals(expected, page.controller.selectedText)
                assertTrue(page.controller.hasSelection)
                assertEquals(SelectionActiveHandle.None, page.controller.state.activeHandle)
                assertTrue(page.controller.state.toolbarRequestKey > 0)
                page.controller.copySelection()
                assertEquals(expected, page.clipboard.copied.last())
                page.controller.clearSelection()
            }
        }
    }

    @Test
    fun should_copyWholeCharacters_when_replacementMovesSelectionEndpointIntoASurrogatePair() = runComposeUiTest {
        val page = SelectionPage("AB")
        setContent { page.Content() }
        awaitText("AB")
        runOnIdle { page.controller.selectAll() }

        runOnIdle { page.source = "A😀Z" }
        awaitText("A😀Z")
        runOnIdle {
            assertEquals("A", page.controller.selectedText)
            assertEquals(1, page.controller.state.range?.end?.charInBlock)
            page.controller.copySelection()
            assertEquals(listOf("A"), page.clipboard.copied)
        }
    }

    @Test
    fun should_clampBothPhysicalHandles_when_documentShrinksDuringACrossedDrag() = runComposeUiTest {
        val page = SelectionPage("甲😀乙")
        setContent { page.Content() }
        awaitText("甲😀乙")
        runOnIdle {
            val controller = page.controller
            controller.selectAll()
            val id = assertNotNull(controller.state.range).start.blockStableId
            controller.beginSelectionHandleDrag(SelectionActiveHandle.End)
            controller.dragSelectionHandleTo(SelectionAnchor(id, 3))
            controller.finishSelectionHandleDrag()
            controller.beginSelectionHandleDrag(SelectionActiveHandle.Start)
            controller.dragSelectionHandleTo(SelectionAnchor(id, 4))
            assertEquals("乙", controller.selectedText)
        }

        runOnIdle { page.source = "😀" }
        awaitText("😀")
        runOnIdle {
            val controller = page.controller
            assertTrue(controller.state.isHandleDrag)
            assertEquals(2, controller.selectionAnchorForHandle(SelectionActiveHandle.Start)?.charInBlock)
            assertEquals(2, controller.selectionAnchorForHandle(SelectionActiveHandle.End)?.charInBlock)
            val id = assertNotNull(controller.state.range).start.blockStableId
            controller.dragSelectionHandleTo(SelectionAnchor(id, 0))
            assertEquals(2, controller.state.range?.end?.charInBlock)
            assertEquals("😀", controller.selectedText)
            controller.finishSelectionHandleDrag()
            controller.copySelection()
            assertEquals(listOf("😀"), page.clipboard.copied)
            assertFalse(controller.state.isHandleDrag)
        }
    }

    @Test
    fun should_cancelDragAndCopy_when_documentUpdateRemovesASelectedBlock() = runComposeUiTest {
        val page = SelectionPage("甲😀乙\n\n尾部")
        setContent { page.Content() }
        awaitText("尾部")
        runOnIdle {
            page.controller.selectAll()
            page.controller.beginSelectionHandleDrag(SelectionActiveHandle.Start)
            assertTrue(page.controller.state.isHandleDrag)
            page.source = "甲😀乙"
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("尾部")).fetchSemanticsNodes().isEmpty() }
        waitForIdle()
        runOnIdle {
            val controller = page.controller
            assertNull(controller.state.range)
            assertFalse(controller.state.isHandleDrag)
            assertEquals(SelectionActiveHandle.None, controller.state.activeHandle)
            assertNull(controller.selectionAnchorForHandle(SelectionActiveHandle.Start))
            assertEquals("", controller.selectedText)
            controller.copySelection()
            assertTrue(page.clipboard.copied.isEmpty())
        }
    }

    private fun ComposeUiTest.awaitText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
    }

    private class SelectionPage(source: String) {
        var source by mutableStateOf(source)
        val clipboard = RecordingClipboard()
        private var currentController: MarkdownSelectionController? = null
        val controller: MarkdownSelectionController get() = assertNotNull(currentController)

        @Composable
        fun Content() = CompositionLocalProvider(LocalClipboard provides clipboard) {
            Box(Modifier.size(320.dp, 240.dp)) {
                Markdown(
                    markdown = source,
                    enableSelection = true,
                    enableScroll = false,
                    components = MarkdownComponents(header = {
                        val controller = LocalMarkdownSelectionController.current
                        SideEffect { currentController = controller }
                    }),
                )
            }
        }
    }

    /** Exercise the actual platform ClipEntry conversion without touching the system clipboard. */
    @OptIn(ExperimentalComposeUiApi::class)
    private class RecordingClipboard : Clipboard {
        val copied = mutableListOf<String>()
        override val nativeClipboard: NativeClipboard = Any()
        override suspend fun getClipEntry(): ClipEntry? = null
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            copied += assertNotNull(clipEntry?.asAwtTransferable).getTransferData(DataFlavor.stringFlavor) as String
        }
    }
}
