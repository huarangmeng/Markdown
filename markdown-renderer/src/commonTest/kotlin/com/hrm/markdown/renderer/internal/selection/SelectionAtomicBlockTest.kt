package com.hrm.markdown.renderer.internal.selection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.internal.core.model.MathBlockModel
import com.hrm.markdown.renderer.internal.core.model.MathBlockWidgetModel
import com.hrm.markdown.renderer.internal.layout.model.BlockWidgetMeasurement
import com.hrm.markdown.renderer.internal.layout.model.LayoutWidgetBlockModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class SelectionAtomicBlockTest {
    @Test
    fun should_highlight_actual_composable_bounds_when_widget_measurement_differs() = runComposeUiTest {
        val latex = "x^2 + y^2"
        val widget = MathBlockWidgetModel(selIdentity(2), latex)
        val block = LayoutWidgetBlockModel(
            identity = selIdentity(1),
            frame = selRect(width = 200f, height = 40f),
            contentFrame = selRect(width = 200f, height = 40f),
            block = MathBlockModel(selIdentity(1), latex, widget),
            widget = widget,
            measurement = BlockWidgetMeasurement(widthPx = 200f, heightPx = 40f),
        )
        var controller: MarkdownSelectionController? = null

        setContent {
            val current = rememberMarkdownSelectionController(
                coroutineScope = rememberCoroutineScope(),
                textMeasurer = rememberTextMeasurer(),
            )
            controller = current
            LaunchedEffect(current) {
                current.updateIndex(listOf(block))
                current.state.range = SelectionRange(
                    start = SelectionAnchor(block.identity.stableId, 0),
                    end = SelectionAnchor(block.identity.stableId, latex.length),
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 200.dp, height = 100.dp)
                    .onGloballyPositioned {
                        current.registry.register(block.identity.stableId, it)
                    }
            )
        }

        waitForIdle()

        runOnIdle {
            val actual = checkNotNull(controller)
            val snapshot = checkNotNull(actual.registry.snapshotOf(block.identity.stableId))
            val highlight = actual.highlightBoxesFor(block.identity.stableId).single()

            assertEquals(snapshot.size.width.toFloat(), highlight.width)
            assertEquals(snapshot.size.height.toFloat(), highlight.height)
            assertNotEquals(block.frame.height, highlight.height)
        }
    }
}
