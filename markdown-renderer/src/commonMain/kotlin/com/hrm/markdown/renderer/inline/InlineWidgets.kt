package com.hrm.markdown.renderer.inline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import com.hrm.codehigh.theme.CodeTheme
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.internal.core.model.InlineModel
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry

@Composable
internal fun SpoilerContent(
    model: InlineModel,
    theme: MarkdownTheme,
    hostTextStyle: TextStyle,
    directiveRegistry: MarkdownDirectiveRegistry,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    latexMeasurer: LatexMeasurerState,
    density: Density,
    textMeasurer: TextMeasurer,
    inlineCodeTheme: CodeTheme?,
) {
    var revealed by remember(model.identity.stableId) { mutableStateOf(false) }
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    val currentOnFootnoteClick = rememberUpdatedState(onFootnoteClick)
    val stableOnLinkClick: (String) -> Unit = remember {
        { url: String ->
            currentOnLinkClick.value?.invoke(url)
        }
    }
    val stableOnFootnoteClick: (String) -> Unit = remember {
        { label: String ->
            currentOnFootnoteClick.value?.invoke(label)
        }
    }
    val annotated = remember(
        model.identity.contentRevision,
        theme,
        revealed,
        hostTextStyle,
        directiveRegistry,
        latexMeasurer,
        density,
        textMeasurer,
        inlineCodeTheme,
    ) {
        val content = buildInlineRenderResultFromModel(
            model = model,
            theme = theme,
            hostTextStyle = hostTextStyle,
            directiveRegistry = directiveRegistry,
            onLinkClick = stableOnLinkClick,
            onFootnoteClick = stableOnFootnoteClick,
            latexMeasurer = latexMeasurer,
            density = density,
            textMeasurer = textMeasurer,
            codeTheme = inlineCodeTheme,
        ).annotated
        if (revealed) {
            buildAnnotatedString {
                withStyle(SpanStyle(background = theme.spoilerColor)) {
                    append(content)
                }
            }
        } else {
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        background = theme.spoilerColor,
                        color = theme.spoilerColor,
                    )
                ) {
                    append(content)
                }
            }
        }
    }
    BasicText(
        text = annotated,
        modifier = Modifier.clickable { revealed = !revealed },
        style = theme.bodyStyle,
    )
}

@Composable
internal fun RubyTextContent(
    base: String,
    annotation: String,
    theme: MarkdownTheme,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = annotation,
            style = theme.bodyStyle.copy(
                fontSize = theme.bodyStyle.fontSize * 0.5f,
                lineHeight = theme.bodyStyle.fontSize * 0.6f,
            ),
        )
        BasicText(
            text = base,
            style = theme.bodyStyle.copy(
                lineHeight = theme.bodyStyle.fontSize * 1.2f,
            ),
        )
    }
}
