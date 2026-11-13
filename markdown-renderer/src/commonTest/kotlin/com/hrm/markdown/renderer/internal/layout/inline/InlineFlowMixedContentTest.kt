package com.hrm.markdown.renderer.internal.layout.inline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.systemLatexFontFamilies
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.ResolvedMarkdownStyle
import com.hrm.markdown.renderer.inline.buildInlineRenderResultFromModel
import com.hrm.markdown.renderer.internal.core.compile.DefaultRenderModelCompiler
import com.hrm.markdown.renderer.internal.core.compile.RenderCompileEnvironment
import com.hrm.markdown.renderer.internal.core.model.DirectiveInlineWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineCodeWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineMathWidgetModel
import com.hrm.markdown.renderer.internal.core.model.ParagraphBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
import com.hrm.markdown.renderer.internal.layout.model.LayoutWidgetRun
import com.hrm.markdown.renderer.internal.selection.buildSelectionIndex
import com.hrm.markdown.runtime.MarkdownDirectivePlugin
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import com.hrm.markdown.runtime.MarkdownInlineDirectiveRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InlineFlowMixedContentTest {
    @Test
    fun should_layout_real_formulas_and_components_between_long_text_runs_at_multiple_scales() {
        for (scale in listOf(1f, 1.6f)) runComposeUiTest {
            val longText = "中文😀混排abcdefghij".repeat(250)
            val wideCode = "wide_component_".repeat(30)
            val source = "$longText\$\\frac{a_1+b^2}{\\sqrt{x}}\$$longText" +
                "`inline()`{% badge text=OK %}$longText`$wideCode`结尾"
            val paragraph = assertIs<ParagraphBlockModel>(DefaultRenderModelCompiler.compile(
                MarkdownParser().parse(source), RenderCompileEnvironment(),
            ).blocks.single())
            val selectedText = buildSelectionIndex(listOf(paragraph)).entries.single().text
            val registry = MarkdownDirectiveRegistry(listOf(badgePlugin {}))
            val theme = ResolvedMarkdownStyle()
            lateinit var measurer: TextMeasurer
            lateinit var latex: LatexMeasurerState
            lateinit var density: Density
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                    measurer = rememberTextMeasurer()
                    latex = rememberLatexMeasurer(latexConfig)
                    density = LocalDensity.current
                }
            }
            runOnIdle {
                val result = buildInlineRenderResultFromModel(
                    paragraph.inline, theme, theme.bodyStyle, registry,
                    latexMeasurer = latex, density = density, textMeasurer = measurer,
                )
                assertEquals(1, result.inlineMathBuildRequests)
                for (width in listOf(120f, 260.5f, 380f)) {
                    val stats = InlineFlowMeasurementStats()
                    val flow = computeInlineFlowLayout(result.flowInput, theme.bodyStyle, density, measurer, width, Int.MAX_VALUE, stats)
                    val lines = buildInlineLayoutLines(paragraph.identity, 0f, 0f, flow, inlineWidgetByPlaceholderId(paragraph.inline))
                    val widgets = lines.flatMap { it.runs }.filterIsInstance<LayoutWidgetRun>()
                    assertEquals(4, widgets.size)
                    assertIs<InlineMathWidgetModel>(widgets[0].widget)
                    assertIs<InlineCodeWidgetModel>(widgets[1].widget)
                    assertIs<DirectiveInlineWidgetModel>(widgets[2].widget)
                    assertIs<InlineCodeWidgetModel>(widgets[3].widget)
                    assertTrue(widgets[0].naturalWidthPx > 0f && widgets[0].frame.height > 0f)
                    assertTrue(widgets[3].naturalWidthPx > width)
                    assertEquals(width, widgets[3].frame.width)
                    assertTrue(stats.characters < selectedText.length * 8L + 8192)
                    lines.forEach { line ->
                        assertTrue(line.frame.width <= width + 0.01f)
                        line.runs.zipWithNext().forEach { (left, right) ->
                            assertTrue(left.frame.left + left.frame.width <= right.frame.left + 0.01f)
                        }
                        line.runs.forEach { run ->
                            assertTrue(run.frame.top >= line.frame.top - 0.01f)
                            assertTrue(run.frame.top + run.frame.height <= line.frame.top + line.frame.height + 0.01f)
                            when (run) {
                                is LayoutTextRun -> assertEquals(
                                    selectedText.substring(checkNotNull(run.sourceStart), checkNotNull(run.sourceEnd)), run.text.text,
                                )
                                is LayoutWidgetRun -> assertEquals(
                                    selectedText.substring(checkNotNull(run.sourceStart), checkNotNull(run.sourceEnd)), run.alternateText,
                                )
                            }
                        }
                    }
                    assertEquals("结尾", (lines.last().runs.last() as LayoutTextRun).text.text)
                }
            }
        }
    }

    @Test
    fun should_paint_and_activate_custom_inline_content_after_wrapped_text_and_math() {
        for (scale in listOf(1f, 1.6f)) runComposeUiTest {
            var clicks = 0
            val document = MarkdownParser().parse(
                "中文😀混排abcdefghij".repeat(8) + "\$\\frac{a+b}{x^2}\$" +
                    "`inline()`{% badge text=OK %}结尾",
            )
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                    Box(Modifier.width(260.dp)) {
                        Markdown(
                            document,
                            enableScroll = false, enableSelection = false,
                            latexConfig = latexConfig, directivePlugins = listOf(badgePlugin { clicks++ }),
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("inline-badge", useUnmergedTree = true).assertExists().performClick()
            assertEquals(1, clicks)
        }
    }

    private fun badgePlugin(onClick: () -> Unit) = object : MarkdownDirectivePlugin {
        override val id = "inline-layout-regression"
        override val inlineDirectiveRenderers: Map<String, MarkdownInlineDirectiveRenderer> = mapOf(
            "badge" to { BasicText("OK", Modifier.testTag("inline-badge").clickable(onClick = onClick)) },
        )
    }

    private val latexConfig = LatexConfig(fontFamilies = systemLatexFontFamilies())
}
