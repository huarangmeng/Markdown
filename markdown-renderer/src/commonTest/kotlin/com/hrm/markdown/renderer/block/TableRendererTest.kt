package com.hrm.markdown.renderer.block

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TableRendererTest {
    @Test
    fun should_use_precomputed_geometry_and_keep_sourceColumnOrder_inRtl() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TableGridLayout(
                    rowCount = 2,
                    columnCount = 2,
                    availableWidthPx = null,
                    fixedColumnWidthsPx = intArrayOf(40, 80),
                    fixedRowHeightsPx = intArrayOf(30, 50),
                ) {
                    Box(Modifier.testTag("r0c0"))
                    Box(Modifier.testTag("r0c1"))
                    Box(Modifier.testTag("r1c0"))
                    Box(Modifier.testTag("r1c1"))
                }
            }
        }

        val first = onNodeWithTag("r0c0").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithTag("r0c1").fetchSemanticsNode().boundsInRoot
        val nextRow = onNodeWithTag("r1c0").fetchSemanticsNode().boundsInRoot

        assertEquals(40f, first.width, 0.5f)
        assertEquals(80f, second.width, 0.5f)
        assertEquals(30f, first.height, 0.5f)
        assertEquals(50f, nextRow.height, 0.5f)
        assertTrue(first.left < second.left, "RTL must not reverse Markdown source columns")
        assertTrue(first.top < nextRow.top)
    }
}
