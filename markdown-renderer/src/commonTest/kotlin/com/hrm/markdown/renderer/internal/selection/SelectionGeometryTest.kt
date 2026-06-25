package com.hrm.markdown.renderer.internal.selection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SelectionGeometryTest {

    @Test
    fun should_hit_run_when_point_inside_rect() {
        val block = inlineTextBlock(id = 1, text = "hello", width = 100f, height = 20f)
        val hit = hitTestRunInBlock(block, localX = 40f, localY = 10f)
        assertNotNull(hit)
        assertEquals(0, hit.lineIndex)
        assertEquals(0, hit.runIndex)
        assertEquals(40f, hit.runLocalX)
        assertEquals(10f, hit.runLocalY)
    }

    @Test
    fun should_snap_to_nearest_run_when_point_outside() {
        val block = inlineTextBlock(id = 1, text = "hello", width = 100f, height = 20f)
        // Point far to the right of the only run -> snap to its right edge.
        val hit = hitTestRunInBlock(block, localX = 500f, localY = 10f)
        assertNotNull(hit)
        assertEquals(0, hit.runIndex)
        assertEquals(100f, hit.runLocalX)
    }

    @Test
    fun should_prefer_vertically_nearest_line_when_snapping() {
        // Three lines stacked vertically at y = 0,20,40 (height 20 each).
        val block = inlineMultiRunBlock(id = 1, runs = listOf("aaa", "bbb", "ccc"), lineHeight = 20f, runWidth = 100f)
        // localY = 45 is inside the third line band (40..60).
        val hit = hitTestRunInBlock(block, localX = 30f, localY = 45f)
        assertNotNull(hit)
        assertEquals(2, hit.lineIndex)
    }

    @Test
    fun should_slice_runs_across_range() {
        val index = buildSelectionIndex(
            selDocument(inlineMultiRunBlock(id = 1, runs = listOf("abc", "de", "fghi")))
        )
        val entry = index.entries.single()
        // Select chars [2, 7): spans run0 tail "c", run1 "de", run2 head "fg".
        val slices = runRangeForBlock(entry, blockCharStart = 2, blockCharEnd = 7)
        assertEquals(3, slices.size)
        assertEquals(listOf(0, 1, 2), slices.map { it.span.lineIndex })
        assertEquals(listOf(2 to 3, 0 to 2, 0 to 2), slices.map { it.startInRun to it.endInRun })
    }

    @Test
    fun should_slice_within_single_run() {
        val index = buildSelectionIndex(
            selDocument(inlineMultiRunBlock(id = 1, runs = listOf("abcdef")))
        )
        val entry = index.entries.single()
        val slices = runRangeForBlock(entry, blockCharStart = 1, blockCharEnd = 4)
        assertEquals(1, slices.size)
        assertEquals(1, slices[0].startInRun)
        assertEquals(4, slices[0].endInRun)
    }

    @Test
    fun should_return_empty_slices_for_empty_or_clamped_range() {
        val index = buildSelectionIndex(
            selDocument(inlineMultiRunBlock(id = 1, runs = listOf("abc")))
        )
        val entry = index.entries.single()
        assertTrue(runRangeForBlock(entry, blockCharStart = 2, blockCharEnd = 2).isEmpty())
        assertTrue(runRangeForBlock(entry, blockCharStart = 5, blockCharEnd = 9).isEmpty())
    }
}
