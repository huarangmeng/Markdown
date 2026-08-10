package com.hrm.markdown.renderer.internal.layout.engine

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
        assertEquals(TextAlign.End, ending.style.textAlign)

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
}
