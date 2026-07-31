package com.hrm.markdown.renderer.internal.selection

import com.hrm.markdown.parser.ast.Table
import com.hrm.markdown.renderer.internal.core.model.TableBlockModel
import com.hrm.markdown.renderer.internal.core.model.BlockQuoteBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnsLayoutBlockModel
import com.hrm.markdown.renderer.internal.core.model.MathBlockModel
import com.hrm.markdown.renderer.internal.core.model.MathBlockWidgetModel
import com.hrm.markdown.renderer.internal.core.model.ParagraphBlockModel
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
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
    fun should_build_full_document_index_directly_from_render_models() {
        val paragraph = ParagraphBlockModel(
            identity = selIdentity(1),
            inline = inlineModelText(id = 10, text = "before"),
        )
        val table = tableBlock(
            id = 2,
            rows = listOf(listOf("H1", "H2"), listOf("A1", "A2")),
        ).block
        val math = MathBlockModel(
            identity = selIdentity(3),
            latex = "x^2 + y^2",
            widget = MathBlockWidgetModel(selIdentity(30), "x^2 + y^2"),
        )

        val index = buildSelectionIndex(listOf(paragraph, table, math))

        assertEquals(listOf(1L, 2L, 3L), index.entries.map { it.stableId })
        assertEquals(listOf("before", "H1\tH2\nA1\tA2", "x^2 + y^2"), index.entries.map { it.text })
    }

    @Test
    fun should_flatten_nested_blocks_in_document_order() {
        val index = buildSelectionIndexFromLayout(
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
        val index = buildSelectionIndexFromLayout(
            selDocument(
                inlineTextBlock(id = 1, text = "before"),
                tableBlock(id = 99, rows = listOf(listOf("H1", "H2"), listOf("A1", "A2"))),
                inlineTextBlock(id = 2, text = "after"),
            )
        )

        assertEquals(listOf(1L, 99L, 2L), index.entries.map { it.stableId })
        assertEquals("H1\tH2\nA1\tA2", index.entryOf(99)!!.text)
    }

    @Test
    fun should_accumulate_run_char_spans() {
        val block = inlineMultiRunBlock(id = 1, runs = listOf("abc", "de", "fghi"))
        val index = buildSelectionIndexFromLayout(
            selDocument(block)
        )
        val entry = index.entries.single()
        val geometry = buildSelectionGeometry(block, entry)
        assertEquals(9, entry.totalChars)
        assertEquals("abcdefghi", entry.text)
        assertEquals(listOf(0, 3, 5), geometry.runs.map { it.charStart })
        assertEquals(listOf(3, 5, 9), geometry.runs.map { it.charEnd })
    }

    @Test
    fun should_compare_and_normalize_anchors_by_document_order() {
        val index = buildSelectionIndexFromLayout(
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
        val index = buildSelectionIndexFromLayout(selDocument(inlineTextBlock(id = 1, text = "abc")))
        assertEquals(SelectionAnchor(1, 3), index.clampAnchor(SelectionAnchor(1, 99)))
        assertEquals(SelectionAnchor(1, 0), index.clampAnchor(SelectionAnchor(1, -5)))
        assertNull(index.clampAnchor(SelectionAnchor(404, 0)))
    }

    @Test
    fun should_map_visible_runs_into_logical_character_space() {
        val block = inlineMultiRunBlock(id = 1, runs = listOf("abc", "de"))
        val index = buildSelectionIndexFromLayout(
            selDocument(block)
        )
        val entry = index.entries.single()
        val span = buildSelectionGeometry(block, entry).runs[1]
        assertEquals(3, span.charStart)
        assertEquals(5, span.charEnd)
    }

    @Test
    fun should_use_explicit_source_ranges_for_repeated_visible_text() {
        val original = inlineMultiRunBlock(id = 1, runs = listOf("same", "same"))
        val block = original.copy(
            lines = original.lines.mapIndexed { index, line ->
                val run = line.runs.single() as LayoutTextRun
                val start = if (index == 0) 0 else 9
                line.copy(runs = listOf(run.copy(sourceStart = start, sourceEnd = start + 4)))
            },
        )
        val entry = SelectionBlockEntry(
            stableId = 1,
            order = 0,
            totalChars = 13,
            text = "same gap same",
        )

        val spans = buildSelectionGeometry(block, entry).runs

        assertEquals(listOf(0, 9), spans.map { it.charStart })
        assertEquals(listOf(4, 13), spans.map { it.charEnd })
    }

    @Test
    fun should_reuse_unchanged_top_level_selection_fragments_during_streaming_updates() {
        fun paragraph(id: Long, revision: Long, text: String): ParagraphBlockModel {
            val identity = RenderIdentity(id, revision, revision, 0)
            return ParagraphBlockModel(
                identity = identity,
                inline = inlineModelText(id * 10, text).copy(identity = identity),
            )
        }

        val builder = IncrementalSelectionIndexBuilder()
        builder.build(
            listOf(
                paragraph(id = 1, revision = 10, text = "stable"),
                paragraph(id = 2, revision = 20, text = "tail"),
            )
        )
        val updated = builder.build(
            listOf(
                paragraph(id = 1, revision = 10, text = "stable"),
                paragraph(id = 2, revision = 21, text = "tail updated"),
            )
        )

        assertEquals(1, builder.lastMetrics.reusedTopLevelBlocks)
        assertEquals(1, builder.lastMetrics.computedTopLevelBlocks)
        assertEquals(listOf("stable", "tail updated"), updated.entries.map { it.text })
    }
}
