package com.hrm.markdown.renderer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import com.hrm.codehigh.theme.CodeTheme
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.renderer.internal.MarkdownEngineHost
import com.hrm.markdown.renderer.internal.RendererFacadeState
import com.hrm.markdown.renderer.internal.compose.ComposeRenderEnvironment
import com.hrm.markdown.renderer.internal.core.compile.computeSemanticRevision
import com.hrm.markdown.renderer.internal.layout.engine.EagerMarkdownLayoutSource
import com.hrm.markdown.renderer.internal.selection.LocalMarkdownSelectionController
import com.hrm.markdown.renderer.internal.selection.SelectionHandlesHost
import com.hrm.markdown.renderer.internal.selection.SelectionToolbarHost
import com.hrm.markdown.renderer.internal.selection.markdownSelectionGestures
import com.hrm.markdown.renderer.internal.selection.rememberMarkdownSelectionController
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry

@Composable
internal fun MarkdownDocumentRenderer(
    document: Document,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme.auto(),
    codeTheme: CodeTheme? = null,
    config: MarkdownConfig = MarkdownConfig.Default,
    scrollState: ScrollState = rememberScrollState(),
    isStreaming: Boolean = false,
    enableScroll: Boolean = true,
    enableSelection: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    imageContent: MarkdownImageRenderer? = null,
    onLinkClick: ((String) -> Unit)? = null,
    directiveRegistry: MarkdownDirectiveRegistry = MarkdownDirectiveRegistry.Empty,
    documentRevision: Long = document.contentHash,
) {
    val renderMode = remember(enableScroll) {
        resolveMarkdownRenderMode(enableScroll = enableScroll)
    }
    val lazyListState = rememberLazyListState()
    // Document is a mutable public AST. A caller may update the same root instance, so reference
    // equality alone is not a sufficient remember key.
    val renderDocumentRevision =
        documentRevision.takeIf { it != 0L } ?: computeSemanticRevision(document)
    ProvideMarkdownTheme(theme) {
        val engineHost = remember { MarkdownEngineHost() }
        val facadeState = remember(
            theme,
            config,
            codeTheme,
            imageContent,
            onLinkClick,
            directiveRegistry,
            isStreaming,
            enableSelection,
        ) {
            RendererFacadeState(
                theme = theme,
                config = config,
                codeTheme = codeTheme,
                imageRenderer = imageContent,
                onLinkClick = onLinkClick,
                directiveRegistry = directiveRegistry,
                isStreaming = isStreaming,
                enableSelection = enableSelection,
            )
        }
        val internalRenderDocument = remember(
            engineHost,
            document,
            renderDocumentRevision,
            theme,
            config,
            directiveRegistry,
            isStreaming,
        ) {
            engineHost.compile(
                document = document,
                facadeState = facadeState,
            )
        }
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val latexMeasurer = rememberLatexMeasurer()
        val diagramHostRegistry = remember { DiagramHostRegistry() }
        BoxWithConstraints(modifier = modifier) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val blockSpacingPx = with(density) { theme.blockSpacing.toPx() }
            val textMeasurer = rememberTextMeasurer()
            val selectionController = if (enableSelection) {
                val selectionScope = rememberCoroutineScope()
                rememberMarkdownSelectionController(selectionScope, textMeasurer)
            } else {
                null
            }
            selectionController?.bindClipboard(LocalClipboard.current)
            val navigationController = rememberMarkdownNavigationController(
                renderMode = renderMode,
                enableScroll = enableScroll,
                scrollState = scrollState,
                lazyListState = lazyListState,
                onLinkClick = onLinkClick,
            )
            val layoutSource = remember(
                engineHost,
                internalRenderDocument,
                facadeState,
                renderMode,
                viewportWidthPx,
                blockSpacingPx,
                density,
                layoutDirection,
                textMeasurer,
                latexMeasurer,
                diagramHostRegistry,
            ) {
                if (renderMode == MarkdownRenderMode.LazyColumn) {
                    engineHost.layoutSession(
                        renderDocument = internalRenderDocument,
                        facadeState = facadeState,
                        viewportWidth = viewportWidthPx,
                        blockSpacing = blockSpacingPx,
                        onLinkClick = navigationController.linkClickDelegate,
                        onFootnoteClick = navigationController.onFootnoteClick,
                        density = density,
                        textMeasurer = textMeasurer,
                        latexMeasurer = latexMeasurer,
                        diagramHostRegistry = diagramHostRegistry,
                        layoutDirection = layoutDirection,
                    )
                } else {
                    EagerMarkdownLayoutSource(
                        engineHost.layout(
                            renderDocument = internalRenderDocument,
                            facadeState = facadeState,
                            viewportWidth = viewportWidthPx,
                            blockSpacing = blockSpacingPx,
                            onLinkClick = navigationController.linkClickDelegate,
                            onFootnoteClick = navigationController.onFootnoteClick,
                            density = density,
                            textMeasurer = textMeasurer,
                            latexMeasurer = latexMeasurer,
                            diagramHostRegistry = diagramHostRegistry,
                            layoutDirection = layoutDirection,
                        )
                    )
                }
            }
            navigationController.footnoteDefinitionItemIndexes =
                layoutSource.metadata.footnoteDefinitionItemIndexes
            if (selectionController != null) {
                LaunchedEffect(internalRenderDocument) {
                    selectionController.updateDocument(internalRenderDocument.blocks)
                }
            }
            Box(
                modifier = if (selectionController != null) {
                    Modifier
                        .onGloballyPositioned { selectionController.registry.setRoot(it) }
                        .markdownSelectionGestures(selectionController)
                } else {
                    Modifier
                },
            ) {
                CompositionLocalProvider(
                    LocalMarkdownSelectionController provides selectionController,
                ) {
                    ProvideRendererContext(
                        document = document,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = navigationController.onFootnoteClick,
                        onFootnoteBackClick = navigationController.onFootnoteBackClick,
                        footnoteNavigationState = navigationController.footnoteNavigationState,
                        imageContent = imageContent,
                        config = config,
                        codeTheme = codeTheme,
                        isStreaming = isStreaming,
                        directiveRegistry = directiveRegistry,
                        diagramHostRegistry = diagramHostRegistry,
                    ) {
                        engineHost.composePainter.Paint(
                            document = layoutSource,
                            environment = ComposeRenderEnvironment(
                                modifier = Modifier.fillMaxWidth(),
                                renderMode = renderMode,
                                enableScroll = enableScroll,
                                scrollState = scrollState,
                                lazyListState = lazyListState,
                                header = header,
                                footer = footer,
                            ),
                        )
                    }
                }
                if (selectionController != null) {
                    SelectionHandlesHost(selectionController)
                    SelectionToolbarHost(selectionController)
                }
            }
        }
    }
}
