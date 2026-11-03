package com.hrm.markdown.renderer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.renderer.internal.selection.LocalMarkdownSelectionController
import com.hrm.markdown.renderer.internal.selection.MarkdownSelectionController
import com.hrm.markdown.renderer.internal.selection.SelectionActiveHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
class MarkdownTextSearchLifecycleTest {
    @Test
    fun should_reveal_real_match_line_in_external_scroll_and_reveal_it_again() = runComposeUiTest {
        val page = SearchPage("![prefix image](image.png =240x500) " +
            "intro ".repeat(250) + "针**叶**[林](https://example.invalid) " +
            "middle ".repeat(250) + "针叶林 " + "tail ".repeat(60))
        setContent { page.Content() }
        waitForIdle()
        val original = page.document
        runOnIdle {
            page.search.highlightAllMatches = true
            page.search.setQuery("针叶林")
        }
        waitForIdle()
        val matches = page.search.matches
        assertEquals(2, matches.size)
        assertEquals(matches[0].blockStableId, matches[1].blockStableId)
        assertTrue(matches[0].startInBlock > 1000)

        assertTrue(reveal(page, matches[0]))
        runOnIdle {
            assertTrue(page.scroll.value > 500)
            page.assertMatchVisible(matches[0])
            assertNull(page.controller.state.range)
            assertEquals(SelectionActiveHandle.None, page.controller.state.activeHandle)
            assertEquals(0, page.menuCalls)
            assertSame(original, page.document)
            assertEquals(0L, page.controller.fallbackTextMeasurementCount)
        }
        val firstScroll = page.scroll.value
        assertTrue(reveal(page, matches[1]))
        runOnIdle {
            page.assertMatchVisible(matches[1])
            assertTrue(page.scroll.value > firstScroll + 300)
        }
        assertTrue(reveal(page, matches[0]))
        page.assertMatchVisible(matches[0])
        var reset: Deferred<Float>? = null
        runOnIdle { reset = page.scope.async { page.scroll.scrollTo(0) } }
        waitUntil { reset?.isCompleted == true }
        assertEquals(0, page.scroll.value)
        assertTrue(reveal(page, matches[0]))
        page.assertMatchVisible(matches[0])
    }

    @Test
    fun should_paint_optional_yellow_background_without_selection_or_reparse() = runComposeUiTest {
        val phrase = "needle ".repeat(9).trim()
        val page = SearchPage("before $phrase after\n\n$phrase tail", enableSelection = true)
        setContent { page.Content() }
        waitForIdle()
        runOnIdle {
            page.search.highlightAllMatches = true
            page.search.allMatchHighlightColor = Color.Yellow.copy(alpha = 0.3f)
            page.search.highlightColor = Color(0xFFFFC107).copy(alpha = 0.7f)
            page.search.setQuery(phrase)
        }
        val matches = page.search.matches
        assertEquals(2, matches.size)
        for (match in matches) {
            val boxes = page.controller.highlightBoxesFor(match.geometryStableId, match.logicalRange, textOnly = true)
            assertTrue(boxes.map { it.top }.distinct().size > 1, "Each hit must wrap onto real text lines")
        }
        fun assertHighlights(active: MarkdownTextSearchMatch? = null, visible: Boolean = true) {
            val pixels = onNodeWithTag("viewport").captureToImage().toPixelMap()
            for (match in matches) {
                val block = assertNotNull(page.controller.registry.coordinatesOf(match.geometryStableId))
                var light = 0
                var dark = 0
                for (box in page.controller.highlightBoxesFor(match.geometryStableId, match.logicalRange, textOnly = true)) {
                    val topLeft = page.viewport.windowToLocal(block.localToWindow(box.topLeft))
                    val bottomRight = page.viewport.windowToLocal(block.localToWindow(box.bottomRight))
                    for (y in (topLeft.y + 1).toInt().coerceAtLeast(0) until (bottomRight.y - 1).toInt().coerceAtMost(pixels.height)) {
                        for (x in (topLeft.x + 1).toInt().coerceAtLeast(0) until (bottomRight.x - 1).toInt().coerceAtMost(pixels.width)) {
                            val c = pixels[x, y]
                            if (c.red > 0.9f && c.green > 0.9f && c.blue in 0.55f..0.85f) light++
                            if (c.red > 0.9f && c.green in 0.75f..0.9f && c.blue < 0.4f) dark++
                        }
                    }
                }
                assertEquals(visible && match != active, light > 10, "Inactive hit must keep its light background")
                assertEquals(visible && match == active, dark > 10, "Only the active hit may be dark")
            }
        }
        assertHighlights()
        for (match in matches) {
            assertTrue(reveal(page, match))
            assertHighlights(active = match)
        }
        runOnIdle {
            assertNull(page.controller.state.range)
            assertEquals(0, page.menuCalls)
            page.search.highlightActiveMatch = false
        }
        assertHighlights()
        runOnIdle { page.search.highlightAllMatches = false }
        assertHighlights(visible = false)
        runOnIdle {
            page.search.highlightAllMatches = true
            page.search.highlightActiveMatch = true
            page.controller.selectAll()
        }
        val selectedRange = page.controller.state.range
        val toolbarKey = page.controller.state.toolbarRequestKey
        runOnIdle { page.search.setQuery("after") }
        assertTrue(reveal(page, page.search.matches.single()))
        runOnIdle {
            assertEquals(selectedRange, page.controller.state.range)
            assertEquals(toolbarKey, page.controller.state.toolbarRequestKey)
            page.search.clear()
            assertEquals(selectedRange, page.controller.state.range)
            assertNull(page.search.activeMatch)
            assertTrue(page.search.matchesByGeometry.isEmpty())
            page.controller.clearSelection()
        }
        assertHighlights(visible = false)
    }

    @Test
    fun should_reject_old_matches_after_query_document_or_binding_changes() = runComposeUiTest {
        val page = SearchPage("needle old")
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("needle") }
        val old = page.search.matches.single()
        assertTrue(reveal(page, old))
        runOnIdle { page.search.setQuery("old") }
        assertFalse(reveal(page, old))
        runOnIdle {
            page.search.setQuery("needle")
            page.document = MarkdownParser().parse("needle new")
        }
        waitForIdle()
        assertFalse(reveal(page, old))
        val replacement = page.search.matches.single()
        assertTrue(reveal(page, replacement))
        runOnIdle { page.shown = false }
        waitForIdle()
        assertTrue(page.search.matches.isEmpty())
        assertFalse(reveal(page, replacement))
    }

    @Test
    fun should_cancel_pending_parent_scroll_when_cleared_or_document_replaced() = runComposeUiTest {
        val page = SearchPage("prefix ".repeat(600) + "needle " + "tail ".repeat(60))
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("needle") }
        lateinit var pending: Deferred<Boolean>
        runOnIdle {
            pending = page.scope.async(start = CoroutineStart.UNDISPATCHED) {
                page.search.reveal(page.search.matches.single())
            }
            page.search.clear()
        }
        waitUntil { pending.isCompleted }
        assertFalse(runBlocking { pending.await() })
        runOnIdle {
            assertEquals("", page.search.query)
            assertTrue(page.search.matches.isEmpty())
            assertNull(page.search.activeMatch)
            page.search.setQuery("needle")
        }
        runOnIdle {
            pending = page.scope.async(start = CoroutineStart.UNDISPATCHED) {
                page.search.reveal(page.search.matches.single())
            }
            page.document = MarkdownParser().parse("replacement")
        }
        waitForIdle()
        waitUntil { pending.isCompleted }
        assertFalse(runBlocking { pending.await() })
        assertNull(page.search.activeMatch)
        assertTrue(page.search.matches.isEmpty())
    }

    @Test
    fun should_attach_preexisting_query_and_release_search_only_resources_when_detached() = runComposeUiTest {
        val page = SearchPage("needle body", initiallyAttached = false)
        setContent { page.Content() }
        waitForIdle()
        runOnIdle {
            assertNull(page.observedController)
            page.search.setQuery("needle")
            assertTrue(page.search.matches.isEmpty())
            page.searchAttached = true
        }
        waitForIdle()
        val match = page.search.matches.single()
        assertTrue(reveal(page, match))
        runOnIdle {
            page.search.clear()
            page.searchAttached = false
        }
        waitForIdle()
        assertNull(page.observedController)
        assertTrue(page.search.matches.isEmpty())
        assertFalse(reveal(page, match))
        runOnIdle {
            page.search.setQuery("needle")
            page.searchAttached = true
        }
        waitForIdle()
        assertFalse(reveal(page, match))
        assertTrue(reveal(page, page.search.matches.single()))
    }

    @Test
    fun should_remove_search_metadata_and_requesters_without_disrupting_selection() = runComposeUiTest {
        val page = SearchPage("needle body", enableSelection = true)
        setContent { page.Content() }
        waitForIdle()
        runOnIdle {
            page.controller.selectAll()
            page.search.setQuery("needle")
        }
        val match = page.search.matches.single()
        assertTrue(reveal(page, match))
        val selection = page.controller.state.range
        runOnIdle { page.searchAttached = false }
        waitForIdle()
        runOnIdle {
            assertEquals(selection, page.controller.state.range)
            assertTrue(page.controller.index.entries.all { it.searchableRanges.isEmpty() })
            assertNull(page.controller.registry.requesterOf(match.geometryStableId))
            assertTrue(page.search.matches.isEmpty())
        }
    }

    @Test
    fun should_reveal_offscreen_lazy_nested_blocks_using_top_level_index_with_header() = runComposeUiTest {
        val page = SearchPage(
            (0 until 80).joinToString("\n\n") { "Filler paragraph $it" } +
                "\n\n> " + "intro ".repeat(250) + "needle " + "tail ".repeat(60),
            internalScroll = true,
        )
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("needle") }
        val match = page.search.matches.single()
        assertNull(page.controller.registry.coordinatesOf(match.geometryStableId))
        assertTrue(reveal(page, match))
        runOnIdle {
            assertTrue(page.markdownState.firstVisibleBlockIndex > 50)
            page.assertMatchVisible(match)
            assertEquals(0L, page.controller.fallbackTextMeasurementCount)
        }
        lateinit var reset: Deferred<Boolean>
        runOnIdle { reset = page.scope.async { page.markdownState.scrollToBlock(0) } }
        waitUntil { reset.isCompleted }
        waitForIdle()
        assertTrue(reveal(page, match))
        page.assertMatchVisible(match)
    }

    @Test
    fun should_reveal_actual_table_cell_line_and_preserve_table_selection_text() = runComposeUiTest {
        val source = "| Left | Right |\n| --- | --- |\n" +
            (0 until 35).joinToString("\n") { "| row $it | filler $it |" } +
            "\n| end | ![prefix](image.png =30x30)针**叶**林 |"
        val page = SearchPage(source, enableSelection = true)
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("针叶林") }
        val match = page.search.matches.single()
        assertTrue(reveal(page, match))
        runOnIdle {
            page.assertMatchVisible(match)
            assertTrue(page.scroll.value > 500)
            assertNotNull(page.controller.registry.coordinatesOf(match.geometryStableId))
            assertEquals(0L, page.controller.fallbackTextMeasurementCount)
            page.controller.selectAll()
            assertTrue(page.controller.selectedText.contains("end\tprefix针叶林"))
        }
    }

    @Test
    fun should_reject_forged_match_metadata_and_old_query_epoch_even_after_query_returns() = runComposeUiTest {
        val page = SearchPage("needle body")
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("needle") }
        val old = page.search.matches.single()
        assertFalse(reveal(page, old.copy(startInBlock = old.startInBlock + 1)))
        assertFalse(reveal(page, old.copy(blockStableId = old.blockStableId + 1)))
        assertFalse(reveal(page, old.copy(geometryStart = 1)))
        runOnIdle { page.search.setQuery("body"); page.search.setQuery("needle") }
        assertFalse(reveal(page, old))
        assertTrue(reveal(page, page.search.matches.single()))
    }

    @Test
    fun should_supersede_pending_reveal_and_propagate_caller_cancellation() = runComposeUiTest {
        val page = SearchPage("prefix ".repeat(600) + "needle " + "middle ".repeat(200) + "target")
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("needle") }
        lateinit var first: Deferred<Boolean>
        lateinit var second: Deferred<Boolean>
        runOnIdle {
            first = page.scope.async(start = CoroutineStart.UNDISPATCHED) {
                page.search.reveal(page.search.matches.single())
            }
            second = page.scope.async(start = CoroutineStart.UNDISPATCHED) {
                page.search.reveal(page.search.matches.single())
            }
        }
        waitUntil(timeoutMillis = 10_000) { first.isCompleted && second.isCompleted }
        assertFalse(runBlocking { first.await() })
        assertTrue(runBlocking { second.await() })
        runOnIdle {
            page.search.setQuery("target")
            first = page.scope.async(start = CoroutineStart.UNDISPATCHED) {
                page.search.reveal(page.search.matches.single())
            }
            first.cancel()
        }
        waitUntil { first.isCompleted }
        assertTrue(first.isCancelled)
        assertNull(page.search.activeMatch)
    }

    @Test
    fun should_center_offscreen_match_in_owned_lazy_scroll_without_moving_visible_match() = runComposeUiTest {
        val page = SearchPage(
            (0 until 80).joinToString("\n\n") { "Filler paragraph $it" } +
                "\n\nneedle at first line\n\n" + (0 until 20).joinToString("\n\n") { "Following paragraph $it" },
            internalScroll = true,
        )
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("needle") }
        val match = page.search.matches.single()
        assertNull(page.controller.registry.coordinatesOf(match.geometryStableId))
        assertTrue(reveal(page, match))
        page.assertMatchCentered(match)
        val top = page.matchLineTop(match)
        assertTrue(reveal(page, match))
        assertEquals(top, page.matchLineTop(match))
        assertNull(page.search.relocation)
    }

    @Test
    fun should_reuse_search_alignment_with_external_occlusion_and_keep_partial_match_at_nearest_edge() = runComposeUiTest {
        val page = SearchPage("before ".repeat(250) + "needle " + "after ".repeat(250), externalOcclusion = 20f to 40f)
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("needle") }
        val match = page.search.matches.single()
        assertTrue(reveal(page, match))
        page.assertMatchCentered(match)
        val top = page.matchLineTop(match)
        assertTrue(reveal(page, match))
        assertEquals(top, page.matchLineTop(match))
        val box = page.controller.highlightBoxesFor(match.geometryStableId, match.logicalRange, textOnly = true).first()
        val bottom = page.viewport.size.height - 40f
        lateinit var moving: Deferred<Float>
        runOnIdle {
            val wantedTop = bottom - box.height / 2f
            moving = page.scope.async { page.scroll.scrollTo((page.scroll.value + top - wantedTop).toInt()) }
        }
        waitUntil { moving.isCompleted }
        waitForIdle()
        assertTrue(reveal(page, match))
        assertTrue(kotlin.math.abs(page.matchLineTop(match) + box.height - bottom) <= 2f)
        runOnIdle { moving = page.scope.async { page.scroll.scrollTo(0) } }
        waitUntil { moving.isCompleted }
        assertTrue(reveal(page, match))
        page.assertMatchCentered(match)
    }

    @Test
    fun should_cancel_search_relocation_when_selection_gesture_takes_over() = runComposeUiTest {
        val page = SearchPage("before ".repeat(500) + "needle " + "after ".repeat(100), enableSelection = true, externalOcclusion = 0f to 0f)
        setContent { page.Content() }
        waitForIdle()
        runOnIdle { page.search.setQuery("needle") }
        lateinit var request: Deferred<Boolean>
        runOnIdle {
            request = page.scope.async(start = CoroutineStart.UNDISPATCHED) { page.search.reveal(page.search.matches.single()) }
            page.controller.state.activeHandle = SelectionActiveHandle.End
        }
        waitUntil { request.isCompleted }
        assertFalse(runBlocking { request.await() })
        assertNull(page.search.relocation)
        lateinit var reset: Deferred<Float>
        runOnIdle {
            page.controller.state.activeHandle = SelectionActiveHandle.None
            reset = page.scope.async { page.scroll.scrollTo(0) }
        }
        waitUntil { reset.isCompleted }
        waitForIdle()
        assertTrue(page.matchLineTop(page.search.matches.single()) >= page.viewport.size.height, "The next request must begin offscreen")
        assertTrue(reveal(page, page.search.matches.single()))
        page.assertMatchCentered(page.search.matches.single())
    }

    private fun ComposeUiTest.reveal(page: SearchPage, match: MarkdownTextSearchMatch): Boolean {
        lateinit var result: Deferred<Boolean>
        runOnIdle { result = page.scope.async { page.search.reveal(match) } }
        waitUntil(timeoutMillis = 10_000) { result.isCompleted }
        waitForIdle()
        return runBlocking { result.await() }
    }

    private class SearchPage(
        source: String,
        val enableSelection: Boolean = false,
        val internalScroll: Boolean = false,
        val externalOcclusion: Pair<Float, Float>? = null,
        initiallyAttached: Boolean = true,
    ) {
        var document by mutableStateOf(MarkdownParser().parse(source))
        var shown by mutableStateOf(true)
        var searchAttached by mutableStateOf(initiallyAttached)
        lateinit var search: MarkdownTextSearchState
        lateinit var markdownState: MarkdownState
        lateinit var scroll: ScrollState
        lateinit var scope: CoroutineScope
        lateinit var controller: MarkdownSelectionController
        var observedController: MarkdownSelectionController? = null
        lateinit var viewport: LayoutCoordinates
        var menuCalls = 0

        @Composable
        fun Content() {
            val state = rememberMarkdownTextSearchState()
            val navigation = rememberMarkdownState()
            val externalScroll = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            SideEffect { search = state; markdownState = navigation; scroll = externalScroll; scope = coroutineScope }
            Box(Modifier.size(320.dp, 240.dp).background(Color.White).testTag("viewport")
                .onGloballyPositioned { viewport = it }) {
                val content: @Composable () -> Unit = {
                    if (shown) Markdown(
                        document = document,
                        textSearchState = if (searchAttached) state else null,
                        state = navigation,
                        enableScroll = internalScroll,
                        enableSelection = enableSelection,
                        components = MarkdownComponents(
                            header = {
                                val current = LocalMarkdownSelectionController.current
                                SideEffect {
                                    observedController = current
                                    if (current != null) controller = current
                                }
                                Box(Modifier.height(32.dp))
                            },
                            image = { _, modifier -> Box(modifier.height(500.dp).background(Color.Blue)) },
                            selectionToolbar = { _, _ -> SideEffect { menuCalls++ } },
                        ),
                    )
                }
                if (internalScroll) content()
                else {
                    val spec = externalOcclusion?.let { (top, bottom) ->
                        rememberMarkdownTextSearchBringIntoViewSpec(state, top, bottom)
                    } ?: LocalBringIntoViewSpec.current
                    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
                        Column(Modifier.fillMaxWidth().verticalScroll(externalScroll)) { content() }
                    }
                }
            }
        }

        fun matchLineTop(match: MarkdownTextSearchMatch): Float {
            val box = controller.highlightBoxesFor(match.geometryStableId, match.logicalRange, textOnly = true).first()
            val block = assertNotNull(controller.registry.coordinatesOf(match.geometryStableId))
            return viewport.windowToLocal(block.localToWindow(box.topLeft)).y
        }

        fun assertMatchCentered(match: MarkdownTextSearchMatch) {
            val box = controller.highlightBoxesFor(match.geometryStableId, match.logicalRange, textOnly = true).first()
            val (top, bottom) = externalOcclusion ?: (0f to 0f)
            val center = (top + viewport.size.height - bottom) / 2f
            assertTrue(kotlin.math.abs(matchLineTop(match) + box.height / 2f - center) <= 2f, "Match must reach the usable viewport center")
            assertEquals(match, search.activeMatch)
        }

        fun assertMatchVisible(match: MarkdownTextSearchMatch) {
            val box = controller.highlightBoxesFor(match.geometryStableId, match.logicalRange, textOnly = true).first()
            val block = assertNotNull(controller.registry.coordinatesOf(match.geometryStableId))
            val top = viewport.windowToLocal(block.localToWindow(box.topLeft)).y
            val bottom = viewport.windowToLocal(block.localToWindow(Offset(box.left, box.bottom))).y
            assertTrue(top >= -1f, "Match line top=$top must be inside viewport")
            assertTrue(bottom <= viewport.size.height + 1f, "Match line bottom=$bottom must be inside viewport")
            assertEquals(match, search.activeMatch)
        }
    }
}
