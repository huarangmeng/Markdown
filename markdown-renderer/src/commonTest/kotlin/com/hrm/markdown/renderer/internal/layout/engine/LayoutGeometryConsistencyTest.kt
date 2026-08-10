package com.hrm.markdown.renderer.internal.layout.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.renderer.DiagramHostRegistry
import com.hrm.markdown.renderer.MarkdownConfig
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.internal.MarkdownEngineHost
import com.hrm.markdown.renderer.internal.RendererFacadeState
import com.hrm.markdown.renderer.internal.layout.LayoutTokens
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutDocumentModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutBibliographyBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutListBlockModel
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LayoutGeometryConsistencyTest {
    @Test
    fun should_count_heading_divider_once_using_density_aware_tokens() = runComposeUiTest {
        var document: InternalLayoutDocumentModel? = null
        var density: Density? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                CaptureLayout("# Heading") { result, currentDensity ->
                    document = result
                    density = currentDensity
                }
            }
        }

        waitForIdle()

        val heading = assertNotNull(document).blocks.single() as LayoutInlineBlockModel
        val expectedDecoration = with(assertNotNull(density)) {
            LayoutTokens.HeadingDividerSpacing.toPx() + MarkdownTheme().dividerThickness.toPx()
        }
        assertClose(
            expectedDecoration,
            heading.frame.height - heading.contentFrame.height,
        )
    }

    @Test
    fun should_use_same_tight_list_spacing_as_compose_renderer() = runComposeUiTest {
        var document: InternalLayoutDocumentModel? = null
        var density: Density? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                CaptureLayout("- first\n- second") { result, currentDensity ->
                    document = result
                    density = currentDensity
                }
            }
        }

        waitForIdle()

        val list = assertNotNull(document).blocks.single() as LayoutListBlockModel
        assertEquals(2, list.items.size)
        assertTrue(list.block.tight)
        val actualSpacing = list.frame.height - list.items.sumOf { it.frame.height.toDouble() }.toFloat()
        val expectedSpacing = with(assertNotNull(density)) {
            LayoutTokens.ListTightItemSpacing.toPx()
        }
        assertClose(expectedSpacing, actualSpacing)
    }

    @Test
    fun should_apply_bibliography_padding_on_all_four_sides() = runComposeUiTest {
        var document: InternalLayoutDocumentModel? = null
        var density: Density? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                CaptureLayout(
                    "[^bibliography]: ref: A reference that can wrap onto another line",
                ) { result, currentDensity ->
                    document = result
                    density = currentDensity
                }
            }
        }

        waitForIdle()

        val bibliography = assertNotNull(document).blocks.single() as LayoutBibliographyBlockModel
        val padding = with(assertNotNull(density)) { LayoutTokens.BibliographyPadding.toPx() }
        assertClose(padding, bibliography.contentFrame.left - bibliography.frame.left)
        assertClose(padding * 2f, bibliography.frame.width - bibliography.contentFrame.width)
        assertClose(padding * 2f, bibliography.frame.height - bibliography.contentFrame.height)
    }

    @Composable
    private fun CaptureLayout(
        markdown: String,
        onResult: (InternalLayoutDocumentModel, Density) -> Unit,
    ) {
        val density = LocalDensity.current
        val theme = remember { MarkdownTheme() }
        val textMeasurer = rememberTextMeasurer()
        val latexMeasurer = rememberLatexMeasurer()
        val host = remember { MarkdownEngineHost() }
        val facadeState = remember(theme) {
            RendererFacadeState(
                theme = theme,
                config = MarkdownConfig.Default,
                codeTheme = null,
                imageRenderer = null,
                onLinkClick = null,
                directiveRegistry = MarkdownDirectiveRegistry.Empty,
                isStreaming = false,
                enableSelection = false,
            )
        }
        val renderDocument = remember(markdown, facadeState) {
            host.compile(MarkdownParser().parse(markdown), facadeState)
        }
        val layout = remember(renderDocument, density, textMeasurer, latexMeasurer) {
            host.layout(
                renderDocument = renderDocument,
                facadeState = facadeState,
                viewportWidth = 320f,
                density = density,
                textMeasurer = textMeasurer,
                latexMeasurer = latexMeasurer,
                diagramHostRegistry = DiagramHostRegistry(),
            )
        }
        SideEffect { onResult(layout, density) }
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.01f, "Expected $expected, got $actual")
    }
}
