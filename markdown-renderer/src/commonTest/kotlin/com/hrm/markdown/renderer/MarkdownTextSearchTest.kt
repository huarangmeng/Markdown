package com.hrm.markdown.renderer

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.renderer.internal.core.compile.DefaultRenderModelCompiler
import com.hrm.markdown.renderer.internal.core.compile.RenderCompileEnvironment
import com.hrm.markdown.renderer.internal.core.model.BlockQuoteBlockModel
import com.hrm.markdown.renderer.internal.core.model.HeadingBlockModel
import com.hrm.markdown.renderer.internal.selection.IncrementalSelectionIndexBuilder
import com.hrm.markdown.renderer.internal.selection.SelectionBlockEntry
import com.hrm.markdown.renderer.internal.selection.SelectionModelIndex
import com.hrm.markdown.renderer.internal.selection.SelectionSearchRange
import com.hrm.markdown.renderer.internal.selection.buildSelectionIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MarkdownTextSearchTest {
    @Test
    fun should_match_across_inline_styles_with_renderer_not_source_offsets() {
        val index = index("before 针**叶**[林](https://example.invalid) after")
        val match = index.findTextMatches("针叶林", true, 7, Any()).single()
        val text = index.entries.single().text
        assertEquals("before 针叶林 after", text)
        assertEquals(7, match.startInBlock)
        assertEquals("针叶林", text.substring(match.startInBlock, match.endInBlock))
        assertEquals(7L, match.documentRevision)
        assertEquals("before 针叶林 after", match.excerpt)
    }

    @Test
    fun should_fold_unicode_whitespace_and_case_without_changing_utf16_offsets() {
        val source = "😀 Å\u00a0\u2003\t\nB 尾"
        val index = index(source, MarkdownSoftBreakPolicy.LineBreak)
        val match = index.findTextMatches(" \tå b\u2003", true, 1, Any()).single()
        val text = index.entries.single().text
        assertEquals(3, match.startInBlock)
        assertEquals("Å\u00a0\u2003\t\nB", text.substring(match.startInBlock, match.endInBlock))
        assertTrue(index.findTextMatches("å b", false, 1, Any()).isEmpty())
        assertEquals(1, index.findTextMatches("Å B", false, 1, Any()).size)
        assertTrue(index.findTextMatches("\u00a0 \t\n", true, 1, Any()).isEmpty())
    }

    @Test
    fun should_keep_repeated_matches_in_document_order_and_never_join_blocks() {
        val index = index("same same\n\nsame\n\nnext")
        val token = Any()
        val matches = index.findTextMatches("same", true, 2, token)
        assertEquals(3, matches.size)
        assertEquals(listOf(0, 5, 0), matches.map { it.startInBlock })
        assertNotEquals(matches[0], matches[1])
        assertNotEquals(matches[1].blockStableId, matches[2].blockStableId)
        assertEquals(matches, index.findTextMatches("same", true, 2, token))
        assertTrue(index.findTextMatches("same next", true, 2, token).isEmpty())
        assertNotEquals(matches, index.findTextMatches("same", true, 3, Any()))
    }

    @Test
    fun should_skip_atomic_content_but_keep_exact_offsets_after_prefix_image() {
        val index = index("![needle alt](image.png)针**叶**林\n\n```text\nneedle\n```\n\n\$\$needle\$\$\n\n`needle`")
        val match = index.findTextMatches("针叶林", true, 1, Any()).single()
        val entry = index.entryOf(match.blockStableId)!!
        assertEquals(entry.text.indexOf("针叶林"), match.startInBlock)
        assertTrue(match.startInBlock > 0)
        assertTrue(index.findTextMatches("needle", true, 1, Any()).isEmpty())
        assertTrue(index.findTextMatches("alt针", true, 1, Any()).isEmpty())
    }

    @Test
    fun should_not_skip_valid_body_match_after_rejecting_an_overlapping_widget_match() {
        val index = index("![x](image.png)xx")
        val match = index.findTextMatches("xx", true, 1, Any()).single()
        assertEquals(1, match.startInBlock)
        assertEquals(3, match.endInBlock)
    }

    @Test
    fun should_preserve_nested_prose_order_and_utf16_surrogate_boundaries() {
        val index = index("> 中文😀\n\n- 中文😀\n- other")
        val matches = index.findTextMatches("😀", true, 1, Any())
        assertEquals(2, matches.size)
        assertEquals(listOf(2, 2), matches.map { it.endInBlock - it.startInBlock })
        assertTrue(index.findTextMatches("\uD83D", true, 1, Any()).isEmpty())
        assertTrue(index.findTextMatches("\uDE00", true, 1, Any()).isEmpty())
    }

    @Test
    fun should_bound_excerpts_around_late_matches_without_splitting_surrogates() {
        val index = index("😀".repeat(80) + "needle" + "😀".repeat(80))
        val match = index.findTextMatches("needle", true, 1, Any()).single()
        assertEquals(160, match.startInBlock)
        assertEquals("…" + "😀".repeat(20) + "needle" + "😀".repeat(20) + "…", match.excerpt)
    }

    @Test
    fun should_search_table_cells_without_changing_table_copy_text_or_joining_cells() {
        val index = index("| first | second |\n| --- | --- |\n| ![alt](image.png)针**叶**林 | 针叶林 |")
        val matches = index.findTextMatches("针叶林", true, 1, Any())
        assertEquals(2, matches.size)
        val entry = index.entries.single()
        assertEquals("first\tsecond\nalt针叶林\t针叶林", entry.text)
        assertEquals(listOf(entry.stableId, entry.stableId), matches.map { it.blockStableId })
        assertNotEquals(matches[0].geometryStableId, matches[1].geometryStableId)
        for (match in matches) {
            assertNotEquals(entry.stableId, match.geometryStableId)
            assertEquals("针叶林", entry.text.substring(match.startInBlock, match.endInBlock))
            val cell = index.geometryEntryOf(match.geometryStableId)!!
            assertEquals("针叶林", cell.text.substring(match.logicalRange.start.charInBlock, match.logicalRange.end.charInBlock))
        }
        assertTrue(index.findTextMatches("alt", true, 1, Any()).isEmpty())
        assertTrue(index.findTextMatches("first second", true, 1, Any()).isEmpty())
        assertTrue(index.findTextMatches("针叶林 针叶林", true, 1, Any()).isEmpty())
    }

    @Test
    fun should_keep_table_cell_geometry_ids_distinct_across_identical_tables_and_prose() {
        val table = "| needle | needle |\n| --- | --- |\n| needle | needle |"
        val index = index("needle\n\n$table\n\nseparator\n\n$table")
        val matches = index.findTextMatches("needle", true, 1, Any())
        assertEquals(9, matches.size)
        assertEquals(matches.size, matches.map { it.geometryStableId }.toSet().size)
        assertEquals(listOf(0, 1, 3), matches.map { index.entryOf(it.blockStableId)!!.topLevelIndex }.distinct())
    }

    @Test
    fun should_track_nested_top_level_owners_for_lazy_navigation() {
        val index = index("first\n\n> quote needle\n\n- list needle\n\nlast")
        val matches = index.findTextMatches("needle", true, 1, Any())
        assertEquals(listOf(1, 2), matches.map { index.entryOf(it.blockStableId)!!.topLevelIndex })
    }

    @Test
    fun should_build_no_search_metadata_when_disabled_and_reuse_fragments_when_enabled() {
        val blocks = DefaultRenderModelCompiler.compile(
            MarkdownParser().parse("needle\n\n| needle |\n| --- |\n| needle |"),
            RenderCompileEnvironment(),
        ).blocks
        val builder = IncrementalSelectionIndexBuilder()
        val disabled = builder.build(blocks)
        assertTrue(disabled.entries.all { it.searchableRanges.isEmpty() })
        assertTrue(disabled.findTextMatches("needle", true, 1, Any()).isEmpty())
        val enabled = builder.build(blocks, includeSearch = true)
        assertEquals(3, enabled.findTextMatches("needle", true, 1, Any()).size)
        assertEquals(disabled.entries.map { it.text }, enabled.entries.map { it.text })
        builder.build(blocks, includeSearch = true)
        assertEquals(0, builder.lastMetrics.computedTopLevelBlocks)
        assertEquals(blocks.size, builder.lastMetrics.reusedTopLevelBlocks)
        assertTrue(builder.build(blocks).entries.all { it.searchableRanges.isEmpty() })
    }

    @Test
    fun should_use_kotlin_simple_case_matching_without_expanding_or_rewriting_offsets() {
        val index = index("Σ σ ς İ i ß ss")
        assertEquals(3, index.findTextMatches("σ", true, 1, Any()).size)
        assertEquals(2, index.findTextMatches("i", true, 1, Any()).size)
        assertEquals(1, index.findTextMatches("ss", true, 1, Any()).size)
        assertEquals(1, index.findTextMatches("ß", true, 1, Any()).size)
    }

    @Test
    fun should_invalidate_cached_heading_text_when_numbering_changes_without_ast_revision() {
        val heading = DefaultRenderModelCompiler.compile(
            MarkdownParser().parse("# needle"),
            RenderCompileEnvironment(),
        ).blocks.single() as HeadingBlockModel
        val builder = IncrementalSelectionIndexBuilder()
        for (prefix in listOf(null, "1", "10", null)) {
            val block = heading.copy(numbering = prefix)
            assertEquals(heading.identity.contentRevision, block.identity.contentRevision)
            val index = builder.build(listOf(block), includeSearch = true)
            val match = index.findTextMatches("needle", true, 1, Any()).single()
            val expectedPrefix = prefix?.plus(' ').orEmpty()
            assertEquals(expectedPrefix + "needle", index.entries.single().text)
            assertEquals(expectedPrefix.length, match.startInBlock)
            assertEquals(expectedPrefix.length + 6, match.endInBlock)
            builder.build(listOf(block), includeSearch = true)
            assertEquals(0, builder.lastMetrics.computedTopLevelBlocks)
            assertEquals(1, builder.lastMetrics.reusedTopLevelBlocks)
        }
    }

    @Test
    fun should_invalidate_nested_heading_prefix_but_reuse_unaffected_root_fragments() {
        val blocks = DefaultRenderModelCompiler.compile(
            MarkdownParser().parse("> # needle\n\nunchanged body"),
            RenderCompileEnvironment(),
        ).blocks
        val quote = blocks.first() as BlockQuoteBlockModel
        val heading = quote.children.single() as HeadingBlockModel
        val builder = IncrementalSelectionIndexBuilder()
        builder.build(blocks, includeSearch = true)
        val numbered = quote.copy(children = listOf(heading.copy(numbering = "10")))
        assertEquals(quote.identity.contentRevision, numbered.identity.contentRevision)
        val index = builder.build(listOf(numbered, blocks.last()), includeSearch = true)
        val match = index.findTextMatches("needle", true, 1, Any()).single()
        assertEquals("10 needle", index.entryOf(match.blockStableId)?.text)
        assertEquals(3, match.startInBlock)
        assertEquals(1, builder.lastMetrics.computedTopLevelBlocks)
        assertEquals(1, builder.lastMetrics.reusedTopLevelBlocks)
    }

    @Test
    fun should_scan_ordered_eligibility_ranges_once_for_large_tables() {
        val cellCount = 1_000
        val text = List(cellCount) { "needle" }.joinToString("\t")
        var rangeReads = 0
        val ranges = object : AbstractList<SelectionSearchRange>() {
            override val size: Int get() = cellCount
            override fun get(index: Int): SelectionSearchRange {
                rangeReads++
                val start = index * 7
                return SelectionSearchRange(start until start + 6, index.toLong() + 1, start, start + 6)
            }
        }
        val entry = SelectionBlockEntry(0L, 0, text.length, text, searchableRanges = ranges)
        val index = SelectionModelIndex(listOf(entry), includeSearch = true)
        rangeReads = 0
        val matches = index.findTextMatches("needle", true, 1, Any())
        assertEquals(cellCount, matches.size)
        assertEquals(List(cellCount) { it * 7 }, matches.map { it.startInBlock })
        assertTrue(rangeReads <= cellCount * 4, "Ordered ranges must not be rescanned per hit: reads=$rangeReads")
    }

    private fun index(
        source: String,
        softBreakPolicy: MarkdownSoftBreakPolicy = MarkdownSoftBreakPolicy.Space,
    ): SelectionModelIndex = buildSelectionIndex(
        DefaultRenderModelCompiler.compile(
            MarkdownParser().parse(source),
            RenderCompileEnvironment(softBreakPolicy = softBreakPolicy),
        ).blocks,
        includeSearch = true,
    )
}
