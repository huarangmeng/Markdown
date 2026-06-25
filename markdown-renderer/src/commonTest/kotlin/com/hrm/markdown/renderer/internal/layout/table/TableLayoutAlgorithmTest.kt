package com.hrm.markdown.renderer.internal.layout.table

import kotlin.test.Test
import kotlin.test.assertEquals

class TableLayoutAlgorithmTest {
    @Test
    fun should_useMaxContentWidths_when_widthIsUnbounded() {
        val widths = computeAutoTableColumnWidths(
            minContentWidths = listOf(40f, 80f),
            maxContentWidths = listOf(120f, 240f),
            availableWidth = null,
        )

        assertEquals(listOf(120f, 240f), widths)
    }

    @Test
    fun should_distributeExtraWidth_when_tableFitsViewport() {
        val widths = computeAutoTableColumnWidths(
            minContentWidths = listOf(40f, 80f),
            maxContentWidths = listOf(120f, 240f),
            availableWidth = 420f,
        )

        assertEquals(listOf(150f, 270f), widths)
    }

    @Test
    fun should_shrinkTowardMinContentWidths_when_preferredWidthOverflowsViewport() {
        val widths = computeAutoTableColumnWidths(
            minContentWidths = listOf(50f, 100f),
            maxContentWidths = listOf(150f, 300f),
            availableWidth = 300f,
        )

        assertEquals(listOf(100f, 200f), widths)
    }

    @Test
    fun should_keepMinContentWidths_when_viewportIsTooNarrow() {
        val widths = computeAutoTableColumnWidths(
            minContentWidths = listOf(80f, 120f),
            maxContentWidths = listOf(160f, 240f),
            availableWidth = 120f,
        )

        assertEquals(listOf(80f, 120f), widths)
    }
}
