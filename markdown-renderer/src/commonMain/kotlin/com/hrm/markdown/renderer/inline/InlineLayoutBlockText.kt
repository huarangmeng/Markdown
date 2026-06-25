package com.hrm.markdown.renderer.inline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.hrm.codehigh.theme.LocalCodeTheme
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.markdown.renderer.LocalCodeHighlightTheme
import com.hrm.markdown.renderer.LocalMarkdownDirectiveRegistry
import com.hrm.markdown.renderer.LocalMarkdownTheme
import com.hrm.markdown.renderer.LocalOnFootnoteClick
import com.hrm.markdown.renderer.LocalOnLinkClick
import com.hrm.markdown.renderer.internal.compose.PaintInlineLayoutContent
import com.hrm.markdown.renderer.internal.core.model.InlineModel
import com.hrm.markdown.renderer.internal.layout.inline.buildInlineLayoutBlockFromResult
import com.hrm.markdown.renderer.internal.layout.inline.computeIntrinsicHeightPx
import com.hrm.markdown.renderer.internal.layout.inline.computeMaxIntrinsicWidthPx
import com.hrm.markdown.renderer.internal.layout.inline.computeMinIntrinsicWidthPx

@Composable
internal fun InlineLayoutBlockText(
    model: InlineModel,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val theme = LocalMarkdownTheme.current
    val directiveRegistry = LocalMarkdownDirectiveRegistry.current
    val onLinkClick = LocalOnLinkClick.current
    val onFootnoteClick = LocalOnFootnoteClick.current
    val latexMeasurer = rememberLatexMeasurer()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val inlineCodeTheme = LocalCodeHighlightTheme.current ?: LocalCodeTheme.current
    val inlineResult = remember(
        model,
        theme,
        directiveRegistry,
        onLinkClick,
        onFootnoteClick,
        latexMeasurer,
        density,
        textMeasurer,
        inlineCodeTheme,
        style,
    ) {
        buildInlineRenderResultFromModel(
            model = model,
            theme = theme,
            hostTextStyle = style,
            directiveRegistry = directiveRegistry,
            onLinkClick = onLinkClick,
            onFootnoteClick = onFootnoteClick,
            latexMeasurer = latexMeasurer,
            density = density,
            textMeasurer = textMeasurer,
            codeTheme = inlineCodeTheme,
        )
    }
    val measurePolicy = remember(inlineResult.flowInput, style, density, textMeasurer, maxLines) {
        inlineLayoutBlockMeasurePolicy(
            input = inlineResult.flowInput,
            style = style,
            density = density,
            textMeasurer = textMeasurer,
            maxLines = maxLines,
        )
    }

    Layout(
        modifier = modifier,
        content = {
            InlineLayoutBlockMeasuredContent(
                model = model,
                style = style,
                theme = theme,
                directiveRegistry = directiveRegistry,
                onLinkClick = onLinkClick,
                onFootnoteClick = onFootnoteClick,
                latexMeasurer = latexMeasurer,
                density = density,
                textMeasurer = textMeasurer,
                maxLines = maxLines,
                inlineCodeTheme = inlineCodeTheme,
                inlineResult = inlineResult,
            )
        },
        measurePolicy = measurePolicy,
    )
}

@Composable
private fun InlineLayoutBlockMeasuredContent(
    model: InlineModel,
    style: TextStyle,
    theme: com.hrm.markdown.renderer.MarkdownTheme,
    directiveRegistry: com.hrm.markdown.runtime.MarkdownDirectiveRegistry,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    latexMeasurer: com.hrm.latex.renderer.measure.LatexMeasurerState,
    density: Density,
    textMeasurer: TextMeasurer,
    maxLines: Int,
    inlineCodeTheme: com.hrm.codehigh.theme.CodeTheme?,
    inlineResult: InlineRenderResult,
) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val layoutBlock = remember(
            model,
            style,
            theme,
            directiveRegistry,
            onLinkClick,
            onFootnoteClick,
            latexMeasurer,
            density,
            textMeasurer,
            maxWidthPx,
            maxLines,
            inlineCodeTheme,
            inlineResult,
        ) {
            buildInlineLayoutBlockFromResult(
                identity = model.identity,
                model = model,
                style = style,
                left = 0f,
                top = 0f,
                width = maxWidthPx,
                theme = theme,
                inlineResult = inlineResult,
                density = density,
                textMeasurer = textMeasurer,
                maxLines = maxLines,
            )
        }

        PaintInlineLayoutContent(
            block = layoutBlock,
            modifier = Modifier,
        )
    }
}

private fun inlineLayoutBlockMeasurePolicy(
    input: com.hrm.markdown.renderer.internal.layout.inline.InlineFlowInput,
    style: TextStyle,
    density: Density,
    textMeasurer: TextMeasurer,
    maxLines: Int,
): MeasurePolicy = object : MeasurePolicy {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurables: List<androidx.compose.ui.layout.Measurable>,
        constraints: Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        val placeable = measurables.singleOrNull()?.measure(constraints)
        val width = placeable?.width ?: constraints.minWidth
        val height = placeable?.height ?: constraints.minHeight
        return layout(width, height) {
            placeable?.placeRelative(0, 0)
        }
    }

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        height: Int,
    ): Int = computeMinIntrinsicWidthPx(
        input = input,
        style = style,
        textMeasurer = textMeasurer,
    )

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        height: Int,
    ): Int = computeMaxIntrinsicWidthPx(
        input = input,
        style = style,
        textMeasurer = textMeasurer,
    )

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        width: Int,
    ): Int = computeIntrinsicHeightPx(
        input = input,
        style = style,
        density = density,
        textMeasurer = textMeasurer,
        maxLines = maxLines,
        widthPx = width,
    )

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        width: Int,
    ): Int = computeIntrinsicHeightPx(
        input = input,
        style = style,
        density = density,
        textMeasurer = textMeasurer,
        maxLines = maxLines,
        widthPx = width,
    )
}
