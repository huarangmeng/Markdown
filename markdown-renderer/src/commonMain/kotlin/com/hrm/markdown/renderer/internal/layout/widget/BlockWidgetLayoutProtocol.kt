package com.hrm.markdown.renderer.internal.layout.widget

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.markdown.renderer.internal.core.model.BlockWidgetModel
import com.hrm.markdown.renderer.internal.core.model.CodeBlockWidgetModel
import com.hrm.markdown.renderer.internal.core.model.DiagramBlockWidgetModel
import com.hrm.markdown.renderer.internal.core.model.MathBlockWidgetModel
import com.hrm.markdown.renderer.internal.layout.engine.LayoutEnvironment
import com.hrm.markdown.renderer.internal.layout.engine.lineHeightPx
import com.hrm.markdown.renderer.internal.layout.model.BlockWidgetMeasurement
import kotlin.math.max

internal fun measureBlockWidget(
    widget: BlockWidgetModel,
    viewportWidthPx: Float,
    environment: LayoutEnvironment,
): BlockWidgetMeasurement {
    return when (widget) {
        is CodeBlockWidgetModel -> measureCodeWidget(widget, viewportWidthPx, environment)
        is MathBlockWidgetModel -> measureMathWidget(widget, viewportWidthPx, environment)
        is DiagramBlockWidgetModel -> measureDiagramWidget(widget, viewportWidthPx, environment)
    }
}

private fun measureCodeWidget(
    widget: CodeBlockWidgetModel,
    viewportWidthPx: Float,
    environment: LayoutEnvironment,
): BlockWidgetMeasurement {
    val lines = widget.code.lineSequence().toList().ifEmpty { listOf(" ") }
    val style = environment.markdownTheme.codeBlockStyle
    val contentWidth = lines.maxOf { line ->
        environment.textMeasurer.measure(
            text = line.ifEmpty { " " },
            style = style,
            constraints = Constraints(maxWidth = Int.MAX_VALUE),
            maxLines = 1,
            softWrap = false,
        ).size.width.toFloat()
    }
    val padding = with(environment.density) { environment.markdownTheme.codeBlockPadding.toPx() }
    val titleHeight = if (widget.title.isNullOrBlank()) {
        0f
    } else {
        environment.lineHeightPx(style) + padding
    }
    val contentWidthPx = max(viewportWidthPx, contentWidth + padding * 2f)
    return BlockWidgetMeasurement(
        widthPx = contentWidthPx,
        heightPx = titleHeight + lines.size * environment.lineHeightPx(style) + padding * 2f,
        scrollableHorizontally = contentWidthPx > viewportWidthPx,
    )
}

private fun measureMathWidget(
    widget: MathBlockWidgetModel,
    viewportWidthPx: Float,
    environment: LayoutEnvironment,
): BlockWidgetMeasurement {
    val config = LatexConfig(
        fontSize = (environment.markdownTheme.mathFontSize * 1.2f).sp,
        theme = environment.markdownTheme.latexTheme,
    )
    val dimensions = environment.latexMeasurer.measure(widget.latex.trim(), config)
    val padding = with(environment.density) { environment.markdownTheme.codeBlockPadding.toPx() }
    val contentHeight = dimensions?.heightPx ?: environment.lineHeightPx(
        TextStyle(fontSize = config.fontSize)
    )
    val contentWidth = dimensions?.widthPx ?: 0f
    return BlockWidgetMeasurement(
        widthPx = max(viewportWidthPx, contentWidth + padding * 2f),
        heightPx = contentHeight + padding * 2f,
        scrollableHorizontally = contentWidth + padding * 2f > viewportWidthPx,
    )
}

private fun measureDiagramWidget(
    widget: DiagramBlockWidgetModel,
    viewportWidthPx: Float,
    environment: LayoutEnvironment,
): BlockWidgetMeasurement {
    val padding = with(environment.density) { environment.markdownTheme.codeBlockPadding.toPx() }
    val cachedHeight = environment.diagramHostRegistry.cachedHeightPx(widget.hostKey)
    if (cachedHeight != null) {
        return BlockWidgetMeasurement(
            widthPx = viewportWidthPx,
            heightPx = cachedHeight + padding * 2f,
        )
    }
    val lineCount = widget.code.lineSequence().count().coerceAtLeast(3)
    val preferredHeight = with(environment.density) {
        when (widget.diagramType.lowercase()) {
            "mermaid" -> 120.dp.toPx() + lineCount * 10.dp.toPx()
            "plantuml" -> 110.dp.toPx() + lineCount * 9.dp.toPx()
            else -> 100.dp.toPx() + lineCount * 8.dp.toPx()
        }
    }
    return BlockWidgetMeasurement(
        widthPx = viewportWidthPx,
        heightPx = preferredHeight.coerceAtLeast(with(environment.density) { 140.dp.toPx() }) +
            padding * 2f,
    )
}
