package com.hrm.markdown.renderer.internal

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.luminance
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.renderer.DiagramHostRegistry
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.internal.compose.DefaultMarkdownComposePainter
import com.hrm.markdown.renderer.internal.compose.MarkdownComposePainter
import com.hrm.markdown.renderer.internal.core.compile.DefaultRenderModelCompiler
import com.hrm.markdown.renderer.internal.core.compile.RenderCompileEnvironment
import com.hrm.markdown.renderer.internal.core.compile.RenderConfigSnapshot
import com.hrm.markdown.renderer.internal.core.compile.RenderThemeSnapshot
import com.hrm.markdown.renderer.internal.core.model.InternalRenderDocumentModel
import com.hrm.markdown.renderer.internal.layout.engine.DefaultMarkdownLayoutEngine
import com.hrm.markdown.renderer.internal.layout.engine.LayoutEnvironment
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutDocumentModel

internal class MarkdownEngineHost(
    private val compiler: DefaultRenderModelCompiler = DefaultRenderModelCompiler,
    private val layoutEngine: DefaultMarkdownLayoutEngine = DefaultMarkdownLayoutEngine,
    val composePainter: MarkdownComposePainter = DefaultMarkdownComposePainter,
) {
    fun compile(
        document: Document,
        facadeState: RendererFacadeState,
    ): InternalRenderDocumentModel {
        return compiler.compile(
            document = document,
            environment = facadeState.toCompileEnvironment(),
        )
    }

    fun layout(
        renderDocument: InternalRenderDocumentModel,
        facadeState: RendererFacadeState,
        viewportWidth: Float,
        blockSpacing: Float = 0f,
        onLinkClick: ((String) -> Unit)? = null,
        onFootnoteClick: ((String) -> Unit)? = null,
        density: Density,
        textMeasurer: TextMeasurer,
        latexMeasurer: LatexMeasurerState,
        diagramHostRegistry: DiagramHostRegistry,
    ): InternalLayoutDocumentModel {
        return layoutEngine.layout(
            document = renderDocument,
            environment = LayoutEnvironment(
                viewportWidth = viewportWidth,
                blockSpacing = blockSpacing,
                markdownTheme = facadeState.theme,
                codeTheme = facadeState.codeTheme,
                onLinkClick = onLinkClick,
                onFootnoteClick = onFootnoteClick,
                density = density,
                textMeasurer = textMeasurer,
                latexMeasurer = latexMeasurer,
                compileEnvironment = facadeState.toCompileEnvironment(),
                diagramHostRegistry = diagramHostRegistry,
            ),
        )
    }
}

internal fun RendererFacadeState.toCompileEnvironment(): RenderCompileEnvironment {
    return RenderCompileEnvironment(
        theme = RenderThemeSnapshot(
            darkMode = theme.isDarkLike(),
        ),
        config = RenderConfigSnapshot(
            enableHeadingNumbering = config.enableHeadingNumbering,
            streaming = isStreaming,
        ),
        directiveRegistry = directiveRegistry,
    )
}

private fun MarkdownTheme.isDarkLike(): Boolean {
    return bodyStyle.color.luminance() < 0.5f
}
