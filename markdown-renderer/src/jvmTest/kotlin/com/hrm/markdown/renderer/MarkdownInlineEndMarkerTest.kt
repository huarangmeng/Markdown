package com.hrm.markdown.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.parser.ast.Highlight
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.parser.block.postprocessors.BlockPostProcessor
import com.hrm.markdown.parser.flavour.ExtendedFlavour
import com.hrm.markdown.renderer.internal.core.compile.DefaultRenderModelCompiler
import com.hrm.markdown.renderer.internal.core.compile.RenderCompileEnvironment
import com.hrm.markdown.renderer.internal.core.model.InlineEndMarkerWidgetModel
import com.hrm.markdown.renderer.internal.core.model.ParagraphBlockModel
import com.hrm.markdown.renderer.internal.core.model.TextAtom
import com.hrm.markdown.renderer.internal.core.model.WidgetAtom
import com.hrm.markdown.renderer.internal.selection.LocalMarkdownSelectionController
import com.hrm.markdown.renderer.internal.selection.MarkdownSelectionController
import com.hrm.markdown.renderer.internal.selection.SelectionAnchor
import com.hrm.markdown.renderer.internal.selection.SelectionRange
import com.hrm.markdown.renderer.internal.selection.buildSelectionIndex
import com.hrm.markdown.renderer.internal.selection.extractSelectedText
import java.awt.datatransfer.DataFlavor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownInlineEndMarkerTest {
    @Test
    fun should_resolveEachRangeWithStableIdentity_when_nestedMarksShareEndpoint() {
        for (ids in listOf(listOf("outer", "inner"), listOf("same", "same"))) {
            val parsed = MarkdownParserConfig.Default.newParser().parse(
                """<mark data-id="${ids[0]}"><mark data-id="${ids[1]}">甲😀</mark></mark>乙""",
            )
            val compiled = DefaultRenderModelCompiler.compile(parsed, RenderCompileEnvironment())
            val paragraph = assertIs<ParagraphBlockModel>(compiled.blocks.single())
            val textAtoms = paragraph.inline.atoms.filterIsInstance<TextAtom>()
            assertEquals(listOf("甲😀", "乙"), textAtoms.map { it.text })
            val terminal = textAtoms.first()
            assertEquals(ids, terminal.marks.map { it.attributes["data-id"] }, "real parser/compiler must retain both ranges")
            assertTrue(terminal.marks.all { it.kind == InlineElementKinds.Highlight && it.isRangeEnd })

            val marker = MarkdownInlineEndMarker(MARKER, diameter = 8.dp, painter = ColorPainter(Color.White))
            val underline = MarkdownInlineUnderline(Color.Transparent, endMarker = marker)
            val requests = mutableListOf<MarkdownInlineDecorationRequest>()
            var disabledCallback: Int? = null
            val decorate: (MarkdownInlineDecorationRequest) -> MarkdownInlineUnderline? = { request ->
                val callbackIndex = requests.size
                requests += request
                if (callbackIndex == disabledCallback) null else underline
            }
            val expectedRequests = ids.asReversed().map {
                MarkdownInlineDecorationRequest(InlineElementKinds.Highlight, "甲😀", attributes = mapOf("data-id" to it))
            }
            val resolved = resolveInlineEndMarkers(compiled, decorate)
            val resolvedParagraph = assertIs<ParagraphBlockModel>(resolved.blocks.single())
            val widgets = resolvedParagraph.inline.atoms.filterIsInstance<WidgetAtom>().map { assertIs<InlineEndMarkerWidgetModel>(it.widget) }
            assertEquals(expectedRequests, requests, "callbacks must run inside-out, including identical attributes")
            assertEquals(listOf(marker, marker), widgets.map { it.marker })
            assertEquals(2, widgets.map { it.identity.stableId }.toSet().size)
            requests.clear()
            assertEquals(resolved, resolveInlineEndMarkers(compiled, decorate), "same source and painters must resolve identically")

            val models = mutableListOf(resolved)
            for (disabled in 0..1) {
                disabledCallback = disabled
                requests.clear()
                val partial = resolveInlineEndMarkers(compiled, decorate)
                val partialParagraph = assertIs<ParagraphBlockModel>(partial.blocks.single())
                val remaining = partialParagraph.inline.atoms.filterIsInstance<WidgetAtom>().single()
                assertEquals(expectedRequests, requests)
                assertEquals(widgets[1 - disabled], remaining.widget, "disabling either callback must preserve the other marker identity")
                assertNotEquals(resolved.identity.layoutRevision, partial.identity.layoutRevision)
                assertNotEquals(resolved.identity.paintRevision, partial.identity.paintRevision)
                models += partial
            }
            assertSame(compiled, resolveInlineEndMarkers(compiled, null))
            assertSame(compiled, resolveInlineEndMarkers(compiled) { null })

            val before = buildSelectionIndex(compiled.blocks)
            val entry = before.entries.single()
            assertEquals("甲😀乙", entry.text)
            assertEquals(4, entry.totalChars)
            val originalParents = listOf(compiled.identity, paragraph.identity, paragraph.inline.identity)
            for (model in models) {
                val current = assertIs<ParagraphBlockModel>(model.blocks.single())
                assertEquals(textAtoms, current.inline.atoms.filterIsInstance<TextAtom>())
                val parents = listOf(model.identity, current.identity, current.inline.identity)
                for ((original, updated) in originalParents.zip(parents)) {
                    assertEquals(original.stableId, updated.stableId)
                    assertNotEquals(original.layoutRevision, updated.layoutRevision)
                    assertNotEquals(original.paintRevision, updated.paintRevision)
                }
                assertEquals(compiled.identity.contentRevision, model.identity.contentRevision)
                assertEquals(paragraph.identity.contentRevision, current.identity.contentRevision)
                assertEquals(current.inline.identity.layoutRevision, current.inline.identity.contentRevision)
                assertNotEquals(paragraph.inline.identity.contentRevision, current.inline.identity.contentRevision)
                val after = buildSelectionIndex(model.blocks)
                assertEquals(before.entries, after.entries)
                for ((start, end, expected) in listOf(Triple(0, 4, "甲😀乙"), Triple(0, 3, "甲😀"), Triple(1, 3, "😀"))) {
                    val range = SelectionRange(SelectionAnchor(entry.stableId, start), SelectionAnchor(entry.stableId, end))
                    assertEquals(expected, extractSelectedText(after, range))
                }
            }
        }
    }

    @Test
    fun should_paintMarkerOnlyAtSemanticRangeEnd_when_rangeSpansBoldRuns() = runComposeUiTest {
        val page = MarkerPage("前缀 ==划线**加粗**== 后缀", painter = null)
        setContent { page.Content() }
        waitForIdle()

        val markers = markerBlobs()
        assertEquals(1, markers.size, "marker must appear exactly once at the range end")
        val marker = markers.single()
        assertTrue(marker.pixelCount < marker.bounds.width * marker.bounds.height, "default marker must remain a circle")
        runOnIdle { page.decorated = false }
        waitForIdle()
        assertEquals(0, markerBlobs().size, "null decoration must remove the marker without changing text")
    }

    @Test
    fun should_paintPainterAndToggleWithoutChangingText_when_decorationStateChanges() = runComposeUiTest {
        val page = MarkerPage("prefix ==plain **BOLD**==      suffix", initiallyDecorated = false)
        setContent { page.Content() }
        waitForIdle()
        val before = textFragments().map { it.layout.layoutInput.text.text to it.bounds }
        assertTrue(before.isNotEmpty())
        assertEquals(0, markerBlobs().size)

        runOnIdle { page.decorated = true }
        waitForIdle()
        val marker = markerBlobs().single()
        assertTrue(marker.bounds.width >= 6 && marker.bounds.height >= 6, "Painter must produce visible pixels")
        assertEquals(marker.bounds.width * marker.bounds.height, marker.pixelCount, "tinted ColorPainter must paint a rectangle, not the default circle")
        val terminal = textFragments().single { "BOLD" in it.layout.layoutInput.text.text }
        val offset = terminal.layout.layoutInput.text.text.indexOf("BOLD") + 3
        val endX = terminal.bounds.left + terminal.layout.getBoundingBox(offset).right
        assertTrue(
            abs(marker.bounds.left - (endX + 3f)) <= 1f,
            "Painter must follow the final bold character: expectedLeft=${endX + 3f}, actualLeft=${marker.bounds.left}, " +
                "lineEnd=${terminal.bounds.left + terminal.layout.getLineRight(0)}, allocatedEnd=${terminal.bounds.right}, " +
                "text='${terminal.layout.layoutInput.text.text}'",
        )
        assertTrue(
            textFragments().last().bounds.right >= before.last().second.right + 10f,
            "the marker must reserve gap plus diameter before the suffix",
        )

        runOnIdle { page.decorated = false }
        waitForIdle()
        assertEquals(0, markerBlobs().size)
        assertEquals(before, textFragments().map { it.layout.layoutInput.text.text to it.bounds })
    }

    @Test
    fun should_paintPainterOnlyAtFinalLine_when_rangeSpansBoldAndWrapping() = runComposeUiTest {
        val page = MarkerPage(
            "==首行**加粗的长文字重复重复重复**\n最后==      后缀",
            columnWidth = 128.dp,
        )
        setContent { page.Content() }
        waitForIdle()

        val fragments = textFragments()
        assertTrue(fragments.any { fragment -> fragment.layout.layoutInput.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold } })
        val terminal = fragments.single { "最后" in it.layout.layoutInput.text.text }
        assertTrue(fragments.count { it.bounds.bottom <= terminal.bounds.top } >= 2, "fixture must really wrap before its explicit line break")
        val marker = markerBlobs().single()
        assertTrue(marker.bounds.top >= terminal.bounds.top, "no marker may remain on an earlier line")
        val endX = terminal.bounds.left + terminal.layout.getBoundingBox(1).right
        assertTrue(abs(marker.bounds.left - (endX + 3f)) <= 1f)
    }

    @Test
    fun should_paintEachIndependentRange_when_adjacentHighlightsHaveIdenticalContentAndAttributes() = runComposeUiTest {
        val attributes = mapOf("data-state" to "same")
        val config = MarkdownParserConfig(flavour = ExtendedFlavour.with(BlockPostProcessor { block ->
            if (block is Paragraph) Paragraph().apply {
                repeat(2) {
                    appendChild(Highlight(attributes).apply { appendChild(Text("SAME")) })
                }
            } else block
        }))
        val page = MarkerPage("SAMESAME", parserConfig = config)
        setContent { page.Content() }
        waitForIdle()

        assertMarkerEndpoints(4, 8)
        assertEquals(setOf(MarkdownInlineDecorationRequest("highlight", "SAME", attributes = attributes)), page.requests.toSet())
        assertEquals("SAMESAME", textFragments().joinToString("") { it.layout.layoutInput.text.text })
    }

    @Test
    fun should_paintAndCopyEachHtmlRange_when_nestedMarkElementsHaveIdenticalAttributes() = runComposeUiTest {
        val page = MarkerPage("""<mark data-state="same"><mark data-state="same">甲😀</mark></mark>乙""", initiallyDecorated = false)
        setContent { page.Content() }
        waitForIdle()
        val plainEnd = textFragments().last().bounds.right
        var originalRange: SelectionRange? = null

        for (decorated in listOf(false, true, false)) {
            runOnIdle { page.decorated = decorated }
            waitForIdle()
            val markers = markerBlobs().sortedBy { it.bounds.left }
            assertEquals(if (decorated) 2 else 0, markers.size)
            assertEquals("甲😀乙", textFragments().joinToString("") { it.layout.layoutInput.text.text })
            if (decorated) {
                val terminal = textFragments().single { "甲😀" in it.layout.layoutInput.text.text }
                val endX = terminal.bounds.left + terminal.layout.getBoundingBox(1).right
                assertTrue(abs(markers[0].bounds.left - (endX + 3f)) <= 1f)
                assertTrue(abs(markers[1].bounds.left - (markers[0].bounds.right + 3f)) <= 1f)
                assertTrue(markers.all { it.bounds.width >= 6 && it.bounds.height >= 6 })
                assertTrue(textFragments().last().bounds.right >= plainEnd + 21f, "both markers must reserve gap plus diameter")
            }
            runOnIdle {
                val controller = page.controller
                controller.selectAll()
                assertEquals("甲😀乙", controller.selectedText)
                val range = assertNotNull(controller.state.range)
                assertEquals(0, range.start.charInBlock)
                assertEquals(4, range.end.charInBlock)
                if (originalRange == null) originalRange = range else assertEquals(originalRange, range)
                controller.copySelection()
                assertTrue(controller.selectRange(range.start.blockStableId, 1, 3))
                assertEquals("😀", controller.selectedText)
                controller.copySelection()
                controller.clearSelection()
            }
            waitForIdle()
        }
        assertEquals(listOf("甲😀乙", "😀", "甲😀乙", "😀", "甲😀乙", "😀"), page.clipboard.copied)
    }

    @Test
    fun should_copyUnchangedTextAndSelection_when_painterToggles() = runComposeUiTest {
        val page = MarkerPage("前缀 ==划线**加粗**== 😀\n下一行", initiallyDecorated = false)
        val expected = "前缀 划线加粗 😀\n下一行"
        var originalRange: SelectionRange? = null
        setContent { page.Content() }
        waitForIdle()

        for (decorated in listOf(false, true, false)) {
            runOnIdle { page.decorated = decorated }
            waitForIdle()
            assertEquals(if (decorated) 1 else 0, markerBlobs().size)
            runOnIdle {
                val controller = page.controller
                controller.selectAll()
                assertEquals(expected, controller.selectedText)
                val range = assertNotNull(controller.state.range)
                assertEquals(0, range.start.charInBlock)
                assertEquals(expected.length, range.end.charInBlock)
                if (originalRange == null) originalRange = range else assertEquals(originalRange, range)
                controller.copySelection()
                controller.clearSelection()
            }
            waitForIdle()
        }
        assertEquals(listOf(expected, expected, expected), page.clipboard.copied)
    }

    @Test
    fun should_wrapPainterInsideColumn_when_endpointHasNoRemainingSpaceAndPainterOverdraws() = runComposeUiTest {
        val painter = object : Painter() {
            override val intrinsicSize = Size.Unspecified
            override fun applyColorFilter(colorFilter: ColorFilter?) = true
            override fun DrawScope.onDraw() {
                drawRect(MARKER, topLeft = Offset(-20f, -20f), size = Size(size.width + 40f, size.height + 40f))
            }
        }
        val page = MarkerPage("==edge==", painter = painter, columnWidth = 40.dp)
        setContent { page.Content() }
        waitForIdle()

        val viewport = onNodeWithTag(VIEWPORT).fetchSemanticsNode().boundsInRoot
        val column = onNodeWithTag(COLUMN).fetchSemanticsNode().boundsInRoot
        assertTrue(viewport.right - column.right >= 100f, "capture must include a visible gutter, not crop at the column edge")
        val marker = markerBlobs().single()
        val right = column.right - viewport.left
        assertTrue(marker.bounds.right <= right, "Painter must not spill into the adjacent column")
        assertTrue(marker.bounds.top >= textFragments().single().bounds.bottom, "the measured marker must wrap onto a new line")
        assertTrue(abs(marker.bounds.left - (column.left - viewport.left + 3f)) <= 1f, "wrapped marker must use the next line origin")
        assertTrue(marker.bounds.width <= 8 && marker.bounds.height <= 8, "Painter output must be clipped to its declared square")
    }

    @Test
    fun should_paintPainterToLogicalLeft_when_singleRunIsRtl() = runComposeUiTest {
        val page = MarkerPage("==אבגד==", direction = LayoutDirection.Rtl, columnWidth = 192.dp)
        setContent { page.Content() }
        waitForIdle()

        val text = textFragments().single()
        val last = text.layout.layoutInput.text.length - 1
        assertEquals(ResolvedTextDirection.Rtl, text.layout.getBidiRunDirection(last), "fixture must use a real RTL text layout")
        val endX = text.bounds.left + text.layout.getBoundingBox(last).left
        val marker = markerBlobs().single()
        assertTrue(abs(marker.bounds.right - (endX - 3f)) <= 1f, "RTL marker must follow the logical end on the left")
        val viewport = onNodeWithTag(VIEWPORT).fetchSemanticsNode().boundsInRoot
        val column = onNodeWithTag(COLUMN).fetchSemanticsNode().boundsInRoot
        assertTrue(marker.bounds.left >= column.left - viewport.left)
    }

    private fun ComposeUiTest.assertMarkerEndpoints(vararg ends: Int) {
        val fragments = textFragments()
        val expected = ends.map { end ->
            var start = 0
            val fragment = fragments.first { item ->
                val contains = end > start && end <= start + item.layout.layoutInput.text.length
                if (!contains) start += item.layout.layoutInput.text.length
                contains
            }
            val offset = end - start - 1
            val box = fragment.layout.getBoundingBox(offset)
            val left = fragment.bounds.left + box.right + 3f
            val top = fragment.bounds.top + (fragment.bounds.height - 8f) / 2f
            Rect(left - 1f, top - 1f, left + 9f, top + 9f)
        }
        // 标记拥有独立行内框，不能与下一段文字重叠。
        val blobs = markerBlobs()
        fun contains(rect: Rect, blob: MarkerBlob) = blob.bounds.left >= rect.left && blob.bounds.right <= rect.right &&
            blob.bounds.top >= rect.top && blob.bounds.bottom <= rect.bottom
        for (rect in expected) {
            assertTrue(blobs.filter { contains(rect, it) }.sumOf { it.pixelCount } > 8, "Each semantic endpoint must have actual marker pixels")
        }
        assertTrue(blobs.all { blob -> expected.any { contains(it, blob) } }, "Markers must not appear away from their range endpoints")
    }

    private fun ComposeUiTest.markerBlobs(): List<MarkerBlob> {
        val pixels = onNodeWithTag(VIEWPORT).captureToImage().toPixelMap()
        val marked = BooleanArray(pixels.width * pixels.height)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            marked[y * pixels.width + x] = color.red > 0.9f && color.green < 0.1f && color.blue > 0.9f
        }
        val blobs = mutableListOf<MarkerBlob>()
        for (point in marked.indices) {
            if (!marked[point]) continue
            val queue = ArrayDeque<Int>()
            queue.add(point)
            marked[point] = false
            var left = pixels.width
            var top = pixels.height
            var right = 0
            var bottom = 0
            var count = 0
            while (queue.isNotEmpty()) {
                val current = queue.removeLast()
                val x = current % pixels.width
                val y = current / pixels.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
                count++
                for (neighbor in listOfNotNull(
                    (current - 1).takeIf { x > 0 },
                    (current + 1).takeIf { x + 1 < pixels.width },
                    (current - pixels.width).takeIf { y > 0 },
                    (current + pixels.width).takeIf { y + 1 < pixels.height },
                )) {
                    if (marked[neighbor]) {
                        marked[neighbor] = false
                        queue.add(neighbor)
                    }
                }
            }
            blobs += MarkerBlob(IntRect(left, top, right, bottom), count)
        }
        return blobs
    }

    private fun ComposeUiTest.textFragments(): List<TextFragment> {
        val viewport = onNodeWithTag(VIEWPORT).fetchSemanticsNode().boundsInRoot
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().map { node ->
                val results = mutableListOf<TextLayoutResult>()
                assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
                val bounds = node.boundsInRoot
                TextFragment(
                    Rect(bounds.left - viewport.left, bounds.top - viewport.top, bounds.right - viewport.left, bounds.bottom - viewport.top),
                    results.single(),
                )
            }
    }

    private data class MarkerBlob(val bounds: IntRect, val pixelCount: Int)
    private data class TextFragment(val bounds: Rect, val layout: TextLayoutResult)

    private class MarkerPage(
        source: String,
        private val painter: Painter? = ColorPainter(Color.White),
        initiallyDecorated: Boolean = true,
        private val columnWidth: Dp = 320.dp,
        private val gap: Dp = 3.dp,
        private val direction: LayoutDirection = LayoutDirection.Ltr,
        parserConfig: MarkdownParserConfig = MarkdownParserConfig.Default,
    ) {
        private val document = parserConfig.newParser().parse(source)
        var decorated by mutableStateOf(initiallyDecorated)
        val requests = mutableListOf<MarkdownInlineDecorationRequest>()
        val clipboard = RecordingClipboard()
        private var currentController: MarkdownSelectionController? = null
        val controller: MarkdownSelectionController get() = assertNotNull(currentController)
        private val interactions = MarkdownInteractions(inlineDecoration = { request ->
            requests += request
            if (!decorated || request.kind != InlineElementKinds.Highlight) null else MarkdownInlineUnderline(
                color = Color.Transparent,
                endMarker = MarkdownInlineEndMarker(color = MARKER, diameter = 8.dp, gap = gap, painter = painter),
            )
        })

        @Composable
        fun Content() = CompositionLocalProvider(
            LocalDensity provides Density(1f),
            LocalLayoutDirection provides direction,
            LocalClipboard provides clipboard,
        ) {
            Box(Modifier.requiredSize(320.dp, 240.dp).background(Color.Black).testTag(VIEWPORT)) {
                Markdown(
                    document = document,
                    modifier = Modifier.width(columnWidth).fillMaxHeight().testTag(COLUMN),
                    colors = MarkdownDefaults.colors(text = Color.White, highlight = Color.Transparent),
                    typography = MarkdownTypography(text = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        lineHeight = 32.sp,
                        textDirection = if (direction == LayoutDirection.Rtl) TextDirection.Rtl else TextDirection.Ltr,
                    )),
                    enableScroll = false,
                    softBreakPolicy = MarkdownSoftBreakPolicy.LineBreak,
                    interactions = interactions,
                    components = MarkdownComponents(header = {
                        val controller = LocalMarkdownSelectionController.current
                        SideEffect { currentController = controller }
                    }),
                )
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private class RecordingClipboard : Clipboard {
        val copied = mutableListOf<String>()
        override val nativeClipboard: NativeClipboard = Any()
        override suspend fun getClipEntry(): ClipEntry? = null
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            copied += assertNotNull(clipEntry?.asAwtTransferable).getTransferData(DataFlavor.stringFlavor) as String
        }
    }

    private companion object {
        val MARKER = Color(0xFFFF00FF)
        const val VIEWPORT = "inline_marker_viewport"
        const val COLUMN = "inline_marker_column"
    }
}
