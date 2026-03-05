package com.hrm.markdown.parser

import com.hrm.markdown.parser.core.LineCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// line cursor — character-by-character scanning with tab expansion

class LineCursorTest {

    @Test
    fun should_peek_current_char() {
        val cursor = LineCursor("hello")
        assertEquals('h', cursor.peek())
    }

    @Test
    fun should_peek_with_offset() {
        val cursor = LineCursor("hello")
        assertEquals('e', cursor.peek(1))
        assertEquals('l', cursor.peek(2))
    }

    @Test
    fun should_return_null_char_at_end() {
        val cursor = LineCursor("")
        assertEquals('\u0000', cursor.peek())
    }

    @Test
    fun should_advance_and_return_char() {
        val cursor = LineCursor("abc")
        assertEquals('a', cursor.advance())
        assertEquals('b', cursor.advance())
        assertEquals('c', cursor.advance())
        assertEquals('\u0000', cursor.advance()) // past end
    }

    @Test
    fun should_track_position_after_advance() {
        val cursor = LineCursor("hello")
        cursor.advance()
        cursor.advance()
        assertEquals(2, cursor.position)
    }

    @Test
    fun should_report_at_end() {
        val cursor = LineCursor("ab")
        assertFalse(cursor.isAtEnd)
        cursor.advance()
        cursor.advance()
        assertTrue(cursor.isAtEnd)
    }

    @Test
    fun should_expand_tab_to_4_space_stop() {
        val cursor = LineCursor("\tx")
        cursor.advance() // tab
        assertEquals(4, cursor.currentColumn)
    }

    @Test
    fun should_expand_tab_mid_line() {
        val cursor = LineCursor("ab\tx")
        cursor.advance() // a -> col 1
        cursor.advance() // b -> col 2
        cursor.advance() // tab -> col 4
        assertEquals(4, cursor.currentColumn)
    }

    @Test
    fun should_advance_spaces_up_to_max() {
        val cursor = LineCursor("    hello")
        val consumed = cursor.advanceSpaces(3)
        assertEquals(3, consumed)
        assertEquals(' ', cursor.peek()) // one space left
    }

    @Test
    fun should_advance_all_spaces() {
        val cursor = LineCursor("   x")
        val consumed = cursor.advanceSpaces()
        assertEquals(3, consumed)
        assertEquals('x', cursor.peek())
    }

    @Test
    fun should_get_rest_of_line() {
        val cursor = LineCursor("hello world")
        cursor.advance(6) // skip "hello "
        assertEquals("world", cursor.rest())
    }

    @Test
    fun should_return_empty_rest_at_end() {
        val cursor = LineCursor("hi")
        cursor.advance(2)
        assertEquals("", cursor.rest())
    }

    @Test
    fun should_detect_blank_rest() {
        val cursor = LineCursor("text   ")
        cursor.advance(4) // skip "text"
        assertTrue(cursor.restIsBlank())
    }

    @Test
    fun should_not_report_blank_rest_with_content() {
        val cursor = LineCursor("text more")
        cursor.advance(4)
        assertFalse(cursor.restIsBlank())
    }

    @Test
    fun should_snapshot_and_restore() {
        val cursor = LineCursor("abcdef")
        cursor.advance(3) // at 'd'
        val snap = cursor.snapshot()
        cursor.advance(2) // at 'f'
        assertEquals('f', cursor.peek())
        cursor.restore(snap)
        assertEquals('d', cursor.peek())
        assertEquals(3, cursor.position)
    }

    @Test
    fun should_report_remaining_chars() {
        val cursor = LineCursor("hello")
        assertEquals(5, cursor.remaining)
        cursor.advance(2)
        assertEquals(3, cursor.remaining)
    }

    @Test
    fun should_get_trimmed_rest() {
        val cursor = LineCursor("text  hello  ")
        cursor.advance(4) // skip "text"
        assertEquals("hello", cursor.restTrimmed())
    }
}
