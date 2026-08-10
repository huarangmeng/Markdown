package com.hrm.markdown.renderer.internal.layout.columns

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColumnWidthResolverTest {
    @Test
    fun should_resolve_percentage_columns_against_width_after_spacing() {
        val widths = resolveColumnWidths(
            values = listOf("30%", "70%"),
            totalWidthPx = 1_000f,
            spacingPx = 10f,
        )

        assertClose(297f, widths[0])
        assertClose(693f, widths[1])
    }

    @Test
    fun should_preserve_pixel_column_and_give_remaining_width_to_flexible_column() {
        val widths = resolveColumnWidths(
            values = listOf("300px", ""),
            totalWidthPx = 1_000f,
            spacingPx = 10f,
        )

        assertEquals(listOf(300f, 690f), widths)
    }

    @Test
    fun should_scale_explicit_columns_when_they_overflow_available_width() {
        val widths = resolveColumnWidths(
            values = listOf("800px", "800px"),
            totalWidthPx = 1_000f,
            spacingPx = 10f,
        )

        assertClose(495f, widths[0])
        assertClose(495f, widths[1])
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.01f, "Expected $expected, got $actual")
    }
}
