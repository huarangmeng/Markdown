package com.hrm.markdown.renderer.internal.selection

import com.hrm.markdown.parser.ast.Table
import com.hrm.markdown.renderer.internal.core.model.TableBlockModel
import com.hrm.markdown.renderer.internal.core.model.BlockQuoteBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnsLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutColumnGroup
import com.hrm.markdown.renderer.internal.layout.model.LayoutColumnsBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutRenderBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTableBlockModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionModelIndexTest {

    @Test
    fun should_flatten_nested_blocks_in_document_order() {
        val index = buildSelectionIndex(
            selDocument(
                inlineTextBlock(id = 1, text = "first"),
                LayoutRenderBlockModel(
                    identity = selIdentity(2),
                    frame = selRect(),
                    contentFrame = selRect(),
                    block = BlockQuoteBlockModel(identity = selIdentity(2), children = emptyList()),
                    children = listOf(
                        inlineTextBlock(id = 3, text = "quoted-a"),
                        inlineTextBlock(id = 4, text = "quoted-b"),
                    ),
                ),
                LayoutColumnsBlockModel(
                    identity = selIdentity(5),
                    frame = selRect(),
                    contentFrame = selRect(),
                    block = ColumnsLayoutBlockModel(
                        identity = selIdentity(5),
                        columns = listOf(ColumnBlockModel(identity = selIdentity(6), width = "", children = emptyList())),
                    ),
                    columns = listOf(
                        LayoutColumnGroup(
                            identity = selIdentity(6),
                            frame = selRect(),
                            contentFrame = selRect(),
                            width = "",
                            children = listOf(inlineTextBlock(id = 7, text = "col")),
                        )
                    ),
                ),
            )
        )

        assertEquals(listOf(1L, 3L, 4L, 7L), index.entries.map { it.stableId })
        assertEquals(listOf(0, 1, 2, 3), index.entries.map { it.order })
    }

    @Test
    fun should_include_table_blocks_as_copyable_entries() {
        val index = buildSelectionIndex(
            selDocument(
                inlineTextBlock(id = 1, text = "before"),
                tableBlock(id = 99, rows = listOf(listOf("H1", "H2"), listOf("A1", "A2"))),
                inlineTextBlock(id = 2, text = "after"),
            )
        )

        assertEquals(listOf(1L, 99L, 2L), index.entries.map { it.stableId })
        assertEquals("H1\tH2\nA1\tA2", index.entryOf(99)!!.text)
        assertTrue(index.entryOf(99)!!.runs.isEmpty())
    }

    @Test
    fun should_accumulate_run_char_spans() {
        val index = buildSelectionIndex(
            selDocument(inlineMultiRunBlock(id = 1, runs = listOf("abc", "de", "fghi")))
        )
        val entry = index.entries.single()
        assertEquals(9, entry.totalChars)
        assertEquals("abcdefghi", entry.text)
        assertEquals(listOf(0, 3, 5), entry.runs.map { it.charStart })
        assertEquals(listOf(3, 5, 9), entry.runs.map { it.charEnd })
    }

    @Test
    fun should_compare_and_normalize_anchors_by_document_order() {
        val index = buildSelectionIndex(
            selDocument(
                inlineTextBlock(id = 1, text = "aaaa"),
                inlineTextBlock(id = 2, text = "bbbb"),
            )
        )
        val a = SelectionAnchor(blockStableId = 1, charInBlock = 2)
        val b = SelectionAnchor(blockStableId = 2, charInBlock = 1)
        assertTrue(index.compare(a, b) < 0)
        assertEquals(SelectionRange(a, b), index.normalize(b, a))

        val sameBlockEarly = SelectionAnchor(1, 1)
        val sameBlockLate = SelectionAnchor(1, 3)
        assertTrue(index.compare(sameBlockEarly, sameBlockLate) < 0)
    }

    @Test
    fun should_clamp_anchor_char_offset() {
        val index = buildSelectionIndex(selDocument(inlineTextBlock(id = 1, text = "abc")))
        assertEquals(SelectionAnchor(1, 3), index.clampAnchor(SelectionAnchor(1, 99)))
        assertEquals(SelectionAnchor(1, 0), index.clampAnchor(SelectionAnchor(1, -5)))
        assertNull(index.clampAnchor(SelectionAnchor(404, 0)))
    }

    @Test
    fun should_map_char_offset_to_run() {
        val index = buildSelectionIndex(
            selDocument(inlineMultiRunBlock(id = 1, runs = listOf("abc", "de")))
        )
        val entry = index.entries.single()
        val (span, inRun) = index.charToRun(entry, 4)!!
        assertEquals(3, span.charStart)
        assertEquals(1, inRun)
    }
}
