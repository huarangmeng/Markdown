package com.hrm.markdown.renderer.internal.selection

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionTextExtractorTest {

    @Test
    fun should_extract_substring_within_single_block() {
        val index = buildSelectionIndex(selDocument(inlineTextBlock(id = 1, text = "hello world")))
        val range = SelectionRange(
            start = SelectionAnchor(1, 0),
            end = SelectionAnchor(1, 5),
        )
        assertEquals("hello", extractSelectedText(index, range))
    }

    @Test
    fun should_join_blocks_with_newline_across_selection() {
        val index = buildSelectionIndex(
            selDocument(
                inlineTextBlock(id = 1, text = "first block"),
                inlineTextBlock(id = 2, text = "second block"),
                inlineTextBlock(id = 3, text = "third block"),
            )
        )
        val range = SelectionRange(
            start = SelectionAnchor(1, 6),
            end = SelectionAnchor(3, 5),
        )
        assertEquals("block\nsecond block\nthird", extractSelectedText(index, range))
    }

    @Test
    fun should_skip_gap_blocks_not_in_index() {
        // Table sits between two inline blocks; it is excluded from the index, so
        // selecting across it must not contribute any text for the table.
        val index = buildSelectionIndex(
            selDocument(
                inlineTextBlock(id = 1, text = "before"),
                LayoutTableBlockModelGap(id = 99),
                inlineTextBlock(id = 2, text = "after"),
            )
        )
        val range = SelectionRange(
            start = SelectionAnchor(1, 0),
            end = SelectionAnchor(2, 5),
        )
        assertEquals("before\nafter", extractSelectedText(index, range))
    }

    @Test
    fun should_return_empty_when_start_equals_end() {
        val index = buildSelectionIndex(selDocument(inlineTextBlock(id = 1, text = "abc")))
        val range = SelectionRange(
            start = SelectionAnchor(1, 2),
            end = SelectionAnchor(1, 2),
        )
        assertEquals("", extractSelectedText(index, range))
    }

    @Test
    fun should_return_empty_when_order_reversed() {
        val index = buildSelectionIndex(
            selDocument(
                inlineTextBlock(id = 1, text = "aaa"),
                inlineTextBlock(id = 2, text = "bbb"),
            )
        )
        // start order (block 2) is after end order (block 1) -> empty.
        val range = SelectionRange(
            start = SelectionAnchor(2, 0),
            end = SelectionAnchor(1, 3),
        )
        assertEquals("", extractSelectedText(index, range))
    }

    @Test
    fun should_clamp_out_of_range_char_offsets() {
        val index = buildSelectionIndex(selDocument(inlineTextBlock(id = 1, text = "abcde")))
        val range = SelectionRange(
            start = SelectionAnchor(1, -10),
            end = SelectionAnchor(1, 999),
        )
        assertEquals("abcde", extractSelectedText(index, range))
    }
}
