package com.hrm.markdown.renderer.internal.layout.engine

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.renderer.DiagramHostRegistry
import com.hrm.markdown.renderer.MarkdownConfig
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.internal.MarkdownEngineHost
import com.hrm.markdown.renderer.internal.RendererFacadeState
import com.hrm.markdown.renderer.internal.layout.list.listItemContentIndentPx
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutDocumentModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutListBlockModel
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ListLayoutEngineTest {
    @Test
    fun should_measure_nested_list_children_with_marker_indent_removed_from_available_width() = runComposeUiTest {
        val markdown = """
- 第一层列表项包含很长的中文描述，并且紧跟多个标点，观察缩进后剩余宽度是否仍能正确换行。
  - 第二层列表项继续使用中英文混排 MixedTextWithoutSpaces 和中文标点（A、B、C）。
        """.trimIndent()
        val theme = MarkdownTheme()
        val viewportWidthPx = 180f
        var document: InternalLayoutDocumentModel? = null
        var unorderedIndentPx = 0f

        setContent {
            val density = LocalDensity.current
            val facadeState = RendererFacadeState(
                theme = theme,
                config = MarkdownConfig.Default,
                codeTheme = null,
                imageRenderer = null,
                onLinkClick = null,
                directiveRegistry = MarkdownDirectiveRegistry.Empty,
                isStreaming = false,
                enableSelection = false,
            )
            unorderedIndentPx = density.listItemContentIndentPx(
                theme = theme,
                taskListItem = false,
                ordered = false,
            )
            val host = MarkdownEngineHost()
            val renderDocument = host.compile(
                document = MarkdownParser().parse(markdown),
                facadeState = facadeState,
            )
            document = host.layout(
                renderDocument = renderDocument,
                facadeState = facadeState,
                viewportWidth = viewportWidthPx,
                blockSpacing = with(density) { theme.blockSpacing.toPx() },
                density = density,
                textMeasurer = rememberTextMeasurer(),
                latexMeasurer = rememberLatexMeasurer(),
                diagramHostRegistry = DiagramHostRegistry(),
            )
        }

        waitForIdle()

        val rootList = assertNotNull(document).blocks.single() as LayoutListBlockModel
        val firstItem = rootList.items.single()
        assertClose(
            expected = viewportWidthPx - unorderedIndentPx,
            actual = firstItem.contentFrame.width,
            label = "top-level list item content width",
        )

        val nestedList = firstItem.children.filterIsInstance<LayoutListBlockModel>().single()
        val nestedItem = nestedList.items.single()
        assertClose(
            expected = firstItem.contentFrame.width - unorderedIndentPx,
            actual = nestedItem.contentFrame.width,
            label = "nested list item content width",
        )
        assertInlineBlocksStayWithinListContent(nestedItem.children, nestedItem.contentFrame.width)
    }

    private fun assertInlineBlocksStayWithinListContent(
        blocks: List<InternalLayoutBlockModel>,
        maxWidthPx: Float,
    ) {
        blocks.forEach { block ->
            when (block) {
                is LayoutInlineBlockModel -> assertTrue(
                    actual = block.contentFrame.width <= maxWidthPx + 0.5f,
                    message = "Inline block width ${block.contentFrame.width} exceeded list content width $maxWidthPx.",
                )

                is LayoutListBlockModel -> block.items.forEach { item ->
                    assertInlineBlocksStayWithinListContent(item.children, item.contentFrame.width)
                }

                else -> Unit
            }
        }
    }

    private fun assertClose(
        expected: Float,
        actual: Float,
        label: String,
    ) {
        assertTrue(
            actual = abs(expected - actual) <= 0.5f,
            message = "Expected $label to be $expected px, got $actual px.",
        )
    }
}
