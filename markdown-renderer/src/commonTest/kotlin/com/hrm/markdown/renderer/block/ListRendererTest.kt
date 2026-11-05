package com.hrm.markdown.renderer.block

import com.hrm.markdown.renderer.MarkdownTypography

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.ListBlock
import com.hrm.markdown.renderer.LocalMarkdownLatexState
import com.hrm.markdown.renderer.LocalMarkdownStyle
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownComponents
import com.hrm.markdown.renderer.MarkdownRenderOptions
import com.hrm.markdown.renderer.ResolvedMarkdownStyle
import com.hrm.markdown.renderer.rememberMarkdownLatexState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ListRendererTest {
    @Test
    fun should_reserveEightDpGapAndAlignWrappedContent_when_tasksUseLtrOrRtl() {
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (density in listOf(Density(1f), Density(2f, 1.5f))) {
                for (checked in listOf(false, true)) runComposeUiTest {
                    setContent {
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            ListSample(
                                "- [${if (checked) "x" else " "}] 任务正文比较长，需要换行以后仍然保留统一间距。",
                                width = 180,
                                density = density,
                            )
                        }
                    }
                    waitForIdle()
                    val lines = onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
                    assertTrue(lines.size > 1, "Expected wrapped task text")
                    val checkbox = checkboxBoundsInRoot(checked)
                    val contentIndent = with(density) { (ResolvedMarkdownStyle().layout.listIndent + 20.dp + 8.dp).toPx() }
                    val expectedStart = if (direction == LayoutDirection.Ltr) contentIndent else
                        with(density) { 180.dp.toPx() } - contentIndent
                    for (line in lines) {
                        val start = if (direction == LayoutDirection.Ltr) line.boundsInRoot.left else line.boundsInRoot.right
                        assertTrue(abs(start - expectedStart) <= 1f, "Every line must use the pre-layout content start")
                        val visibleGap = if (direction == LayoutDirection.Ltr) line.boundsInRoot.left - checkbox.right else
                            checkbox.left - line.boundsInRoot.right
                        assertTrue(visibleGap >= with(density) { 8.dp.toPx() }, "Expected at least 8.dp of visible separation")
                    }
                }
            }
        }
    }

    @Test
    fun should_centerCheckboxOnFirstTextLine_when_systemFontScaleChanges() = runComposeUiTest {
        setContent {
            ListSample("- [x] 系统大字体任务正文", width = 240,
                density = Density(density = 2f, fontScale = 1.5f))
        }
        waitForIdle()
        val firstLine = onNodeWithText("系统大字体", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(checkboxCenterInRoot(true) - firstLine.center.y) <= 1f)
    }

    @Test
    fun should_centerCheckboxOnFirstTextLine_when_fontSizeScriptOrWrappingChanges() {
        for (fontSize in listOf(16, 24, 32)) {
            for (checked in listOf(false, true)) {
                for (text in listOf(
                    "中文任务正文比较长，需要换行以后仍然对齐第一行。",
                    "English task text wraps onto multiple lines and stays aligned.",
                )) runComposeUiTest {
                    setContent {
                        ListSample("- [${if (checked) "x" else " "}] $text", width = 180,
                            style = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp))
                    }
                    waitForIdle()
                    val textLines = onAllNodes(hasText("", substring = true), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                    assertTrue(textLines.size > 1, "Expected wrapped task text: font=$fontSize, checked=$checked, text=$text")
                    val firstLine = textLines.minBy { it.boundsInRoot.top }.boundsInRoot
                    assertTrue(abs(checkboxCenterInRoot(checked) - firstLine.center.y) <= 1f,
                        "Checkbox must align with the first line: font=$fontSize, checked=$checked, text=$text")
                }
            }
        }
    }

    @Test
    fun should_propagateFirstTextCenterThroughLegacyContainers_when_renderingAstDirectly() = runComposeUiTest {
        setContent {
            val theme = ResolvedMarkdownStyle().copy(typography = MarkdownTypography(text = TextStyle(fontSize = 32.sp, lineHeight = 48.sp)))
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalMarkdownStyle provides theme) {
                val list = remember { MarkdownParser().parse("- [x] 中文任务正文").children.filterIsInstance<ListBlock>().single() }
                val latexState = rememberMarkdownLatexState(MarkdownRenderOptions.Default.latexConfig)
                CompositionLocalProvider(LocalMarkdownLatexState provides latexState) {
                    Box(Modifier.size(300.dp, 200.dp)) { ListBlockRenderer(list) }
                }
            }
        }
        waitForIdle()
        assertTrue(abs(checkboxCenterInRoot(true) - onNodeWithText("中文任务正文", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y) <= 1f)
        assertEquals(52f, onNodeWithText("中文任务正文", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left)
    }

    @Test
    fun should_centerCheckboxOnHeading_when_taskStartsWithHeading() = runComposeUiTest {
        setContent { ListSample("- [x] ## 任务标题\n\n  标题后的正文") }
        waitForIdle()
        assertTrue(abs(checkboxCenterInRoot(true) - onNodeWithText("任务标题", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y) <= 1f)
    }

    @Test
    fun should_keepCheckboxAtTop_when_taskStartsWithImage() = runComposeUiTest {
        setContent {
            ListSample("- [x] ![任务图片](preview://image)\n\n  图片后的正文", components = MarkdownComponents(image = { _, modifier ->
                Box(modifier.height(72.dp).testTag("task-image"))
            }))
        }
        waitForIdle()
        val imageTop = onNodeWithTag("task-image", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertEquals(imageTop + 10f, checkboxCenterInRoot(true))
    }

    @Test
    fun should_alignOrderedMarkersWithFirstBaseline_when_textUsesDifferentScripts() {
        for (enableScroll in listOf(false, true)) runComposeUiTest {
            setContent {
                ListSample("1. 中文列表正文\n2. English list text", enableScroll = enableScroll)
            }
            waitForIdle()
            assertEquals(onNodeWithText("中文列表正文", useUnmergedTree = true).firstBaselineInRoot(), onNodeWithText("1.", useUnmergedTree = true).firstBaselineInRoot())
            assertEquals(onNodeWithText("English list text", useUnmergedTree = true).firstBaselineInRoot(), onNodeWithText("2.", useUnmergedTree = true).firstBaselineInRoot())
        }
    }

    @Test
    fun should_alignBulletWithFirstLine_when_largeTextWraps() = runComposeUiTest {
        val text = "中文列表正文比较长，需要换行以后仍然对齐第一行。"
        setContent { ListSample("- $text", width = 180, style = TextStyle(fontSize = 20.sp, lineHeight = 32.sp)) }
        waitForIdle()
        val firstLine = onNodeWithText("中文列表正文", substring = true, useUnmergedTree = true)
        assertEquals(firstLine.firstBaselineInRoot(), onNodeWithText("•", useUnmergedTree = true).firstBaselineInRoot())
        assertTrue(firstLine.textLayout().layoutInput.text.length < text.length)
    }

    @Test
    fun should_shareEnoughMarkerWidth_when_numberingCrossesDigitBoundary() = runComposeUiTest {
        setContent {
            ListSample(
                "99. 第一个编号跨位数测试，正文需要换行。\n100. 第二个编号跨位数测试，正文需要换行。",
                width = 240,
                style = TextStyle(fontSize = 24.sp, lineHeight = 36.sp),
            )
        }
        waitForIdle()
        val first = onNodeWithText("第一个编号", substring = true, useUnmergedTree = true)
        val second = onNodeWithText("第二个编号", substring = true, useUnmergedTree = true)
        assertEquals(first.fetchSemanticsNode().positionInRoot.x, second.fetchSemanticsNode().positionInRoot.x)
        for ((marker, line) in listOf("99." to first, "100." to second)) {
            val node = onNodeWithText(marker, useUnmergedTree = true)
            assertEquals(1, node.textLayout().lineCount, "Marker $marker must stay on one line")
            assertFalse(node.textLayout().hasVisualOverflow, "Marker $marker must not be clipped")
            assertEquals(line.firstBaselineInRoot(), node.firstBaselineInRoot())
            assertTrue(node.fetchSemanticsNode().boundsInRoot.right <= line.fetchSemanticsNode().boundsInRoot.left)
        }
    }

    @Test
    fun should_keepMarkerAtTop_when_itemStartsWithImage() {
        for (markdown in listOf(
            "- ![列表图片](preview://image)\n\n  图片之后的说明",
            "- ![列表图片](preview://image) 图片之后的同段说明",
        )) runComposeUiTest {
            setContent {
                ListSample(markdown, components = MarkdownComponents(image = { _, modifier ->
                    Box(modifier.height(72.dp).testTag("list-image"))
                }))
            }
            waitForIdle()
            assertEquals(
                onNodeWithTag("list-image", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y,
                onNodeWithText("•", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y,
            )
        }
    }

    @Test
    fun should_alignMarkerWithHeadingBaseline_when_itemStartsWithHeading() = runComposeUiTest {
        setContent { ListSample("1. ## 列表标题\n\n   标题后的正文") }
        waitForIdle()
        assertEquals(onNodeWithText("列表标题", useUnmergedTree = true).firstBaselineInRoot(), onNodeWithText("1.", useUnmergedTree = true).firstBaselineInRoot())
    }

    @Test
    fun should_keepMarkersOnOneLineAndAligned_when_layoutIsRtl() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ListSample("99. 第一项正文\n100. 第二项正文", style = TextStyle(fontSize = 24.sp, lineHeight = 36.sp))
            }
        }
        waitForIdle()
        for ((marker, text) in listOf("99." to "第一项正文", "100." to "第二项正文")) {
            val markerNode = onNodeWithText(marker, useUnmergedTree = true)
            val textNode = onNodeWithText(text, useUnmergedTree = true)
            assertEquals(1, markerNode.textLayout().lineCount)
            assertFalse(markerNode.textLayout().hasVisualOverflow)
            assertEquals(textNode.firstBaselineInRoot(), markerNode.firstBaselineInRoot())
            assertTrue(markerNode.fetchSemanticsNode().boundsInRoot.left >= textNode.fetchSemanticsNode().boundsInRoot.right)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.checkboxCenterInRoot(checked: Boolean): Float {
    return checkboxBoundsInRoot(checked).center.y
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.checkboxBoundsInRoot(checked: Boolean): Rect {
    val target = (if (checked) ResolvedMarkdownStyle().colors.taskChecked else ResolvedMarkdownStyle().colors.taskUnchecked).toArgb()
    val pixels = onRoot().captureToImage().toPixelMap()
    var top = pixels.height
    var bottom = -1
    var left = pixels.width
    var right = -1
    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
        if (pixels[x, y].toArgb() == target) {
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
            left = minOf(left, x)
            right = maxOf(right, x)
        }
    }
    assertTrue(bottom >= top, "Expected a painted checkbox")
    return Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
}

@Composable
private fun ListSample(
    markdown: String,
    width: Int = 360,
    style: TextStyle = ResolvedMarkdownStyle().bodyStyle,
    enableScroll: Boolean = false,
    components: MarkdownComponents = MarkdownComponents.Default,
    density: Density = Density(1f),
) {
    val document = remember(markdown) { MarkdownParser().parse(markdown) }
    CompositionLocalProvider(LocalDensity provides density) {
        Box(Modifier.size(width.dp, 500.dp)) {
            Markdown(
                document = document,
                modifier = Modifier.fillMaxSize(),
                colors = ResolvedMarkdownStyle().copy(typography = MarkdownTypography(text = style)).colors,
                typography = ResolvedMarkdownStyle().copy(typography = MarkdownTypography(text = style)).typography,
                layout = ResolvedMarkdownStyle().copy(typography = MarkdownTypography(text = style)).layout,
                diagramTheme = ResolvedMarkdownStyle().copy(typography = MarkdownTypography(text = style)).diagramTheme,
                enableScroll = enableScroll,
                enableSelection = false,
                components = components,
            )
        }
    }
}

private fun SemanticsNodeInteraction.firstBaselineInRoot(): Float {
    val coordinates = generateSequence(fetchSemanticsNode().layoutInfo.coordinates) { it.parentCoordinates }
        .first { it[FirstBaseline] != AlignmentLine.Unspecified }
    return coordinates.positionInRoot().y + coordinates[FirstBaseline]
}

private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    return layouts.single()
}
