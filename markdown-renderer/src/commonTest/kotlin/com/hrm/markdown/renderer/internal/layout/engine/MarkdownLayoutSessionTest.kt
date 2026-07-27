package com.hrm.markdown.renderer.internal.layout.engine

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.renderer.DiagramHostRegistry
import com.hrm.markdown.renderer.MarkdownConfig
import com.hrm.markdown.renderer.MarkdownRenderMode
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.ProvideMarkdownTheme
import com.hrm.markdown.renderer.internal.MarkdownEngineHost
import com.hrm.markdown.renderer.internal.RendererFacadeState
import com.hrm.markdown.renderer.internal.compose.ComposeRenderEnvironment
import com.hrm.markdown.renderer.internal.compose.DefaultMarkdownComposePainter
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownLayoutSessionTest {
    @Test
    fun should_layout_only_composed_blocks_and_cache_each_result_in_lazy_column() = runComposeUiTest {
        val markdown = (1..200).joinToString("\n\n") { "block $it" }
        val theme = MarkdownTheme()
        var renderBlockCount = 0
        var session: MarkdownLayoutSession? = null

        setContent {
            ProvideMarkdownTheme(theme) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val latexMeasurer = rememberLatexMeasurer()
                val host = remember { MarkdownEngineHost() }
                val facadeState = remember {
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
                val renderDocument = remember {
                    host.compile(
                        document = MarkdownParser().parse(markdown),
                        facadeState = facadeState,
                    )
                }
                val layoutSession = remember(density, textMeasurer, latexMeasurer) {
                    host.layoutSession(
                        renderDocument = renderDocument,
                        facadeState = facadeState,
                        viewportWidth = 240f,
                        blockSpacing = with(density) { theme.blockSpacing.toPx() },
                        density = density,
                        textMeasurer = textMeasurer,
                        latexMeasurer = latexMeasurer,
                        diagramHostRegistry = DiagramHostRegistry(),
                    )
                }
                renderBlockCount = renderDocument.blocks.size
                session = layoutSession

                DefaultMarkdownComposePainter.Paint(
                    document = layoutSession,
                    environment = ComposeRenderEnvironment(
                        modifier = Modifier.width(240.dp).height(120.dp),
                        renderMode = MarkdownRenderMode.LazyColumn,
                        enableScroll = true,
                    ),
                )
            }
        }

        waitForIdle()

        runOnIdle {
            val actualSession = checkNotNull(session)
            assertTrue(actualSession.laidOutBlockCount in 1 until renderBlockCount)

            val laidOutBeforeCacheHit = actualSession.laidOutBlockCount
            val first = actualSession.blockAt(0)
            assertSame(first, actualSession.blockAt(0))
            assertEquals(laidOutBeforeCacheHit, actualSession.laidOutBlockCount)
        }
    }
}
