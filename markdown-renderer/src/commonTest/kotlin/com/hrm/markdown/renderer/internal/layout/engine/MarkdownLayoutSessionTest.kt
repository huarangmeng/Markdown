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
import kotlin.time.TimeSource

@OptIn(ExperimentalTestApi::class)
class MarkdownLayoutSessionTest {
    @Test
    fun should_reuse_unchanged_block_layout_across_streaming_snapshots() = runComposeUiTest {
        var firstSession: MarkdownLayoutSession? = null
        var secondSession: MarkdownLayoutSession? = null

        setContent {
            val theme = MarkdownTheme()
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
                        isStreaming = true,
                        enableSelection = false,
                    )
                }
                val documents = remember {
                    val parser = MarkdownParser()
                    listOf(
                        host.compile(parser.parse("stable\n\ntail"), facadeState),
                        host.compile(parser.parse("stable\n\ntail updated"), facadeState),
                    )
                }
                val registry = remember { DiagramHostRegistry() }
                firstSession = remember(density, textMeasurer, latexMeasurer) {
                    host.layoutSession(
                        renderDocument = documents[0],
                        facadeState = facadeState,
                        viewportWidth = 240f,
                        density = density,
                        textMeasurer = textMeasurer,
                        latexMeasurer = latexMeasurer,
                        diagramHostRegistry = registry,
                    )
                }
                secondSession = remember(density, textMeasurer, latexMeasurer) {
                    host.layoutSession(
                        renderDocument = documents[1],
                        facadeState = facadeState,
                        viewportWidth = 240f,
                        density = density,
                        textMeasurer = textMeasurer,
                        latexMeasurer = latexMeasurer,
                        diagramHostRegistry = registry,
                    )
                }
            }
        }

        waitForIdle()

        runOnIdle {
            val first = checkNotNull(firstSession)
            val second = checkNotNull(secondSession)
            assertEquals(first.stableIdAt(0), second.stableIdAt(0))
            assertSame(first.blockAt(0), second.blockAt(0))
            assertEquals(first.stableIdAt(1), second.stableIdAt(1))
            assertTrue(first.blockAt(1) !== second.blockAt(1))
        }
    }

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
            assertTrue(actualSession.prefetchRequestCount > 0)

            val laidOutBeforeCacheHit = actualSession.laidOutBlockCount
            val first = actualSession.blockAt(0)
            assertSame(first, actualSession.blockAt(0))
            assertEquals(laidOutBeforeCacheHit, actualSession.laidOutBlockCount)
        }
    }

    @Test
    fun should_bound_inline_math_work_to_composed_blocks_on_first_frame() = runComposeUiTest {
        val blockCount = 1_000
        val formulasPerBlock = 3
        val markdown = (1..blockCount).joinToString("\n\n") { index ->
            "block $index with \$x_$index^2\$, \$y_$index^2\$, and \$z_$index^2\$"
        }
        val theme = MarkdownTheme()
        val started = TimeSource.Monotonic.markNow()
        var host: MarkdownEngineHost? = null
        var session: MarkdownLayoutSession? = null
        var recreateSession: (() -> MarkdownLayoutSession)? = null

        setContent {
            ProvideMarkdownTheme(theme) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val latexMeasurer = rememberLatexMeasurer()
                val currentHost = remember { MarkdownEngineHost() }
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
                    currentHost.compile(
                        document = MarkdownParser().parse(markdown),
                        facadeState = facadeState,
                    )
                }
                val layoutSession = remember(density, textMeasurer, latexMeasurer) {
                    currentHost.layoutSession(
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
                host = currentHost
                session = layoutSession
                recreateSession = {
                    currentHost.layoutSession(
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
            val metrics = checkNotNull(host).inlineLayoutMetrics()
            val totalFormulaCount = blockCount * formulasPerBlock

            assertTrue(actualSession.laidOutBlockCount in 1 until blockCount)
            assertEquals(actualSession.laidOutBlockCount.toLong(), metrics.renderResultComputations)
            assertEquals(actualSession.laidOutBlockCount.toLong(), metrics.flowLayoutComputations)
            assertEquals(
                actualSession.laidOutBlockCount.toLong() * formulasPerBlock,
                metrics.inlineMathBuildRequests,
            )
            assertTrue(metrics.inlineMathBuildRequests < totalFormulaCount.toLong())

            println(
                "Inline math first-frame benchmark: ${started.elapsedNow()}, " +
                    "laidOut=${actualSession.laidOutBlockCount}/$blockCount, " +
                    "inlineMathBuilds=${metrics.inlineMathBuildRequests}/$totalFormulaCount"
            )

            val firstBlock = actualSession.blockAt(0)
            val secondSession = checkNotNull(recreateSession).invoke()
            assertSame(firstBlock, secondSession.blockAt(0))
            val afterCacheHit = checkNotNull(host).inlineLayoutMetrics()
            assertEquals(metrics.renderResultRequests, afterCacheHit.renderResultRequests)
            assertEquals(metrics.renderResultComputations, afterCacheHit.renderResultComputations)
            assertEquals(metrics.inlineMathBuildRequests, afterCacheHit.inlineMathBuildRequests)
            assertEquals(metrics.flowLayoutRequests, afterCacheHit.flowLayoutRequests)
            assertEquals(metrics.flowLayoutComputations, afterCacheHit.flowLayoutComputations)
        }
    }
}
