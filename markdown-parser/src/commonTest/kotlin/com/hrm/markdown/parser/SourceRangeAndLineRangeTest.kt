package com.hrm.markdown.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// source position — line/column/offset with comparison

class SourcePositionTest {

    @Test
    fun should_compare_by_offset() {
        val a = SourcePosition(0, 0, 5)
        val b = SourcePosition(1, 0, 10)
        assertTrue(a < b)
        assertTrue(b > a)
    }

    @Test
    fun should_be_equal_with_same_offset() {
        val a = SourcePosition(0, 5, 5)
        val b = SourcePosition(0, 5, 5)
        assertEquals(a, b)
        assertEquals(0, a.compareTo(b))
    }

    @Test
    fun should_have_zero_constant() {
        val zero = SourcePosition.ZERO
        assertEquals(0, zero.line)
        assertEquals(0, zero.column)
        assertEquals(0, zero.offset)
    }
}

// source range — start/end positions, length, contains, overlaps, shift

class SourceRangeTest {

    @Test
    fun should_calculate_length() {
        val range = SourceRange(
            start = SourcePosition(0, 0, 5),
            end = SourcePosition(0, 10, 15)
        )
        assertEquals(10, range.length)
    }

    @Test
    fun should_have_zero_length_for_empty() {
        assertEquals(0, SourceRange.EMPTY.length)
    }

    @Test
    fun should_contain_offset_within_range() {
        val range = SourceRange(
            start = SourcePosition(0, 0, 10),
            end = SourcePosition(0, 0, 20)
        )
        assertTrue(range.contains(10))  // start inclusive
        assertTrue(range.contains(15))
        assertFalse(range.contains(20)) // end exclusive
        assertFalse(range.contains(9))
    }

    @Test
    fun should_detect_overlapping_ranges() {
        val a = SourceRange(
            start = SourcePosition(0, 0, 0),
            end = SourcePosition(0, 0, 10)
        )
        val b = SourceRange(
            start = SourcePosition(0, 0, 5),
            end = SourcePosition(0, 0, 15)
        )
        assertTrue(a.overlaps(b))
        assertTrue(b.overlaps(a))
    }

    @Test
    fun should_not_overlap_adjacent_ranges() {
        val a = SourceRange(
            start = SourcePosition(0, 0, 0),
            end = SourcePosition(0, 0, 10)
        )
        val b = SourceRange(
            start = SourcePosition(0, 0, 10),
            end = SourcePosition(0, 0, 20)
        )
        assertFalse(a.overlaps(b))
    }

    @Test
    fun should_shift_range_by_delta() {
        val range = SourceRange(
            start = SourcePosition(1, 0, 10),
            end = SourcePosition(2, 0, 20)
        )
        val shifted = range.shift(linesDelta = 5, offsetDelta = 100)
        assertEquals(6, shifted.start.line)
        assertEquals(7, shifted.end.line)
        assertEquals(110, shifted.start.offset)
        assertEquals(120, shifted.end.offset)
    }

    @Test
    fun should_preserve_columns_on_shift() {
        val range = SourceRange(
            start = SourcePosition(0, 3, 3),
            end = SourcePosition(0, 8, 8)
        )
        val shifted = range.shift(linesDelta = 1, offsetDelta = 10)
        assertEquals(3, shifted.start.column)
        assertEquals(8, shifted.end.column)
    }
}

// line range — line-based range with contains, overlaps, shift, expand

class LineRangeTest {

    @Test
    fun should_calculate_line_count() {
        val range = LineRange(2, 5)
        assertEquals(3, range.lineCount)
    }

    @Test
    fun should_contain_line_within_range() {
        val range = LineRange(2, 5)
        assertTrue(range.contains(2))  // start inclusive
        assertTrue(range.contains(4))
        assertFalse(range.contains(5)) // end exclusive
        assertFalse(range.contains(1))
    }

    @Test
    fun should_detect_overlapping_line_ranges() {
        val a = LineRange(0, 5)
        val b = LineRange(3, 8)
        assertTrue(a.overlaps(b))
        assertTrue(b.overlaps(a))
    }

    @Test
    fun should_not_overlap_adjacent_line_ranges() {
        val a = LineRange(0, 5)
        val b = LineRange(5, 10)
        assertFalse(a.overlaps(b))
    }

    @Test
    fun should_shift_line_range() {
        val range = LineRange(3, 7)
        val shifted = range.shift(10)
        assertEquals(13, shifted.startLine)
        assertEquals(17, shifted.endLine)
    }

    @Test
    fun should_expand_to_cover_both_ranges() {
        val a = LineRange(3, 7)
        val b = LineRange(5, 10)
        val expanded = a.expand(b)
        assertEquals(3, expanded.startLine)
        assertEquals(10, expanded.endLine)
    }

    @Test
    fun should_expand_with_non_overlapping_range() {
        val a = LineRange(0, 3)
        val b = LineRange(7, 10)
        val expanded = a.expand(b)
        assertEquals(0, expanded.startLine)
        assertEquals(10, expanded.endLine)
    }

    @Test
    fun should_have_zero_line_count_for_empty_range() {
        val range = LineRange(5, 5)
        assertEquals(0, range.lineCount)
    }
}
