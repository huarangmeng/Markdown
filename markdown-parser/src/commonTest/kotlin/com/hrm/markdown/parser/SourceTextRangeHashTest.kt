package com.hrm.markdown.parser

import com.hrm.markdown.parser.core.SourceText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SourceTextRangeHashTest {
    @Test
    fun should_return_same_hash_for_equal_line_sequences_at_different_offsets() {
        val source = SourceText.of("a\nb\nx\na\nb\n")

        assertEquals(
            source.contentHash(LineRange(0, 2)),
            source.contentHash(LineRange(3, 5)),
        )
    }

    @Test
    fun should_change_range_hash_when_any_line_changes() {
        val first = SourceText.of("a\nb\n")
        val second = SourceText.of("a\nc\n")

        assertNotEquals(
            first.contentHash(LineRange(0, 2)),
            second.contentHash(LineRange(0, 2)),
        )
    }

    @Test
    fun should_preserve_range_hashes_when_edit_fast_reuses_unchanged_lines() {
        val original = SourceText.of("alpha\nbeta\ngamma\ndelta\n")
        val edited = SourceText.applyEditFast(
            current = original,
            offset = original.lineStart(1),
            deleteLength = "beta\n".length,
            insertText = "replacement\nwith two lines\n",
        )
        val rebuilt = SourceText.of(edited.content)

        for (start in 0 until edited.lineCount) {
            for (end in start + 1..edited.lineCount) {
                assertEquals(
                    rebuilt.contentHash(LineRange(start, end)),
                    edited.contentHash(LineRange(start, end)),
                )
            }
        }
    }
}
