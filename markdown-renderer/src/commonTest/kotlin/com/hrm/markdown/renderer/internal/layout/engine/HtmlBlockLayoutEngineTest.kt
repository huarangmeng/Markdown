package com.hrm.markdown.renderer.internal.layout.engine

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.renderer.DiagramHostRegistry
import com.hrm.markdown.renderer.MarkdownConfig
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.internal.MarkdownEngineHost
import com.hrm.markdown.renderer.internal.RendererFacadeState
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutDocumentModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutRenderBlockModel
import com.hrm.markdown.renderer.internal.layout.inline.runPlacements
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class HtmlBlockLayoutEngineTest {
    @Test
    fun should_apply_html_alignment_to_final_inline_run_geometry() = runComposeUiTest {
        var document: InternalLayoutDocumentModel? = null

        setContent {
            val density = LocalDensity.current
            val theme = MarkdownTheme()
            val facadeState = RendererFacadeState(
                theme = theme,
                config = MarkdownConfig.Default,
                codeTheme = null,
                imageRenderer = null,
                onLinkClick = null,
                directiveRegistry = MarkdownDirectiveRegistry.Empty,
                isStreaming = false,
                enableSelection = false,
            )
            val host = MarkdownEngineHost()
            val renderDocument = host.compile(
                document = MarkdownParser().parse(
                    "<div><p align=\"center\"><strong>centered</strong></p>" +
                        "<p align=\"right\">ending</p></div>"
                ),
                facadeState = facadeState,
            )
            document = host.layout(
                renderDocument = renderDocument,
                facadeState = facadeState,
                viewportWidth = 320f,
                blockSpacing = with(density) { theme.blockSpacing.toPx() },
                density = density,
                textMeasurer = rememberTextMeasurer(),
                latexMeasurer = rememberLatexMeasurer(),
                diagramHostRegistry = DiagramHostRegistry(),
            )
        }

        waitForIdle()

        val root = assertNotNull(document).blocks.single() as LayoutRenderBlockModel
        val div = root.children.single() as LayoutRenderBlockModel
        val centered = div.children.first() as LayoutInlineBlockModel
        val ending = div.children.last() as LayoutInlineBlockModel
        assertEquals(TextAlign.Center, centered.style.textAlign)
        assertEquals(TextAlign.Right, ending.style.textAlign)

        val centeredPlacement = centered.runPlacements().single()
        val expectedCenteredX = floor(
            centered.contentFrame.left - centered.frame.left +
                (centered.contentFrame.width - centered.lines.single().frame.width) / 2f
        ).toInt()
        assertEquals(expectedCenteredX, centeredPlacement.x)

        val endingPlacement = ending.runPlacements().single()
        val expectedEndingX = floor(
            ending.contentFrame.left - ending.frame.left +
                ending.contentFrame.width - ending.lines.single().frame.width
        ).toInt()
        assertEquals(expectedEndingX, endingPlacement.x)
    }

    @Test
    fun should_keep_physical_alignment_and_resolve_logical_alignment_in_rtl() = runComposeUiTest {
        var document: InternalLayoutDocumentModel? = null

        setContent {
            val density = LocalDensity.current
            val theme = MarkdownTheme()
            val facadeState = RendererFacadeState(
                theme = theme,
                config = MarkdownConfig.Default,
                codeTheme = null,
                imageRenderer = null,
                onLinkClick = null,
                directiveRegistry = MarkdownDirectiveRegistry.Empty,
                isStreaming = false,
                enableSelection = false,
            )
            val host = MarkdownEngineHost()
            val renderDocument = host.compile(
                document = MarkdownParser().parse(
                    "<div><p align=\"left\">left</p>" +
                        "<p align=\"right\">right</p>" +
                        "<p style=\"text-align:start\">start</p>" +
                        "<p style=\"text-align:end\">end</p></div>"
                ),
                facadeState = facadeState,
            )
            document = host.layout(
                renderDocument = renderDocument,
                facadeState = facadeState,
                viewportWidth = 320f,
                blockSpacing = with(density) { theme.blockSpacing.toPx() },
                density = density,
                textMeasurer = rememberTextMeasurer(),
                latexMeasurer = rememberLatexMeasurer(),
                diagramHostRegistry = DiagramHostRegistry(),
                layoutDirection = LayoutDirection.Rtl,
            )
        }

        waitForIdle()

        val root = assertNotNull(document).blocks.single() as LayoutRenderBlockModel
        val div = root.children.single() as LayoutRenderBlockModel
        val paragraphs = div.children.map { it as LayoutInlineBlockModel }
        assertEquals(
            listOf(TextAlign.Left, TextAlign.Right, TextAlign.Start, TextAlign.End),
            paragraphs.map { it.style.textAlign },
        )

        fun leftX(block: LayoutInlineBlockModel): Int = floor(
            block.contentFrame.left - block.frame.left
        ).toInt()
        fun rightX(block: LayoutInlineBlockModel): Int = floor(
            block.contentFrame.left - block.frame.left +
                block.contentFrame.width - block.lines.single().frame.width
        ).toInt()

        assertEquals(leftX(paragraphs[0]), paragraphs[0].runPlacements().single().x)
        assertEquals(rightX(paragraphs[1]), paragraphs[1].runPlacements().single().x)
        assertEquals(rightX(paragraphs[2]), paragraphs[2].runPlacements().single().x)
        assertEquals(leftX(paragraphs[3]), paragraphs[3].runPlacements().single().x)
    }
}
