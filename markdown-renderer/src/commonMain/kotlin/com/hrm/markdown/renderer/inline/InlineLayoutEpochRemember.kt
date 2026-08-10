package com.hrm.markdown.renderer.inline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import com.hrm.codehigh.theme.CodeTheme
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.markdown.renderer.MarkdownConfig
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.internal.layout.inline.InlineLayoutEpoch
import com.hrm.markdown.renderer.internal.layout.inline.inlineLayoutEpoch
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry

/** Compose lifecycle adapter for the pure layout-epoch factory. */
@Composable
internal fun rememberInlineLayoutEpoch(
    theme: MarkdownTheme,
    codeTheme: CodeTheme?,
    directiveRegistry: MarkdownDirectiveRegistry,
    config: MarkdownConfig?,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    density: Density,
    textMeasurer: TextMeasurer,
    latexMeasurer: LatexMeasurerState,
): InlineLayoutEpoch = remember(
    theme,
    codeTheme,
    directiveRegistry,
    config,
    onLinkClick,
    onFootnoteClick,
    density.density,
    density.fontScale,
    textMeasurer,
    latexMeasurer,
) {
    inlineLayoutEpoch(
        theme = theme,
        codeTheme = codeTheme,
        directiveRegistry = directiveRegistry,
        config = config,
        onLinkClick = onLinkClick,
        onFootnoteClick = onFootnoteClick,
        density = density,
        textMeasurer = textMeasurer,
        latexMeasurer = latexMeasurer,
    )
}
