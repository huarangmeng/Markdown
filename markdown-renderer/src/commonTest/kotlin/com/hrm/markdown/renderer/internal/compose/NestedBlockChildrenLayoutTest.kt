package com.hrm.markdown.renderer.internal.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.MarkdownRenderMode
import com.hrm.markdown.renderer.LocalMarkdownDirectiveRegistry
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.core.model.BlockQuoteBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnsLayoutBlockModel
import com.hrm.markdown.renderer.internal.core.model.CustomContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.DirectiveBlockModel
import com.hrm.markdown.renderer.internal.core.model.FallbackContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.InternalRenderBlockModel
import com.hrm.markdown.renderer.internal.core.model.TabBlockModel
import com.hrm.markdown.renderer.internal.core.model.TabItemBlockModel
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutDocumentModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutColumnGroup
import com.hrm.markdown.renderer.internal.layout.model.LayoutColumnsBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineLine
import com.hrm.markdown.renderer.internal.layout.model.LayoutRect
import com.hrm.markdown.renderer.internal.layout.model.LayoutRenderBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutSize
import com.hrm.markdown.renderer.internal.layout.model.LayoutTabBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTabGroup
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
import com.hrm.markdown.runtime.DirectiveBlockRenderScope
import com.hrm.markdown.runtime.MarkdownBlockDirectiveRenderer
import com.hrm.markdown.runtime.MarkdownDirectivePlugin
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NestedBlockChildrenLayoutTest {
    @Test
    fun blockquote_layout_children_are_stacked() = assertSecondBlockStartsBelowFirst(
        document = document(
            renderBlock(
                block = BlockQuoteBlockModel(
                    identity = identity(1),
                    children = emptyList(),
                ),
                children = inlineChildren("quote first paragraph", "quote second paragraph"),
            )
        ),
        first = "quote first paragraph",
        second = "quote second paragraph",
    )

    @Test
    fun columns_layout_children_are_stacked() = assertSecondBlockStartsBelowFirst(
        document = document(
            LayoutColumnsBlockModel(
                identity = identity(10),
                frame = rect(height = 100f),
                contentFrame = rect(height = 100f),
                block = ColumnsLayoutBlockModel(
                    identity = identity(11),
                    columns = listOf(
                        ColumnBlockModel(
                            identity = identity(12),
                            width = "",
                            children = emptyList(),
                        )
                    ),
                ),
                columns = listOf(
                    LayoutColumnGroup(
                        identity = identity(13),
                        frame = rect(height = 100f),
                        contentFrame = rect(height = 100f),
                        width = "",
                        children = inlineChildren("column first paragraph", "column second paragraph"),
                    )
                ),
            )
        ),
        first = "column first paragraph",
        second = "column second paragraph",
    )

    @Test
    fun tab_layout_children_are_stacked() = assertSecondBlockStartsBelowFirst(
        document = document(
            LayoutTabBlockModel(
                identity = identity(20),
                frame = rect(height = 100f),
                contentFrame = rect(height = 100f),
                block = TabBlockModel(
                    identity = identity(21),
                    items = listOf(
                        TabItemBlockModel(
                            identity = identity(22),
                            title = "Tab",
                            children = emptyList(),
                        )
                    ),
                ),
                tabs = listOf(
                    LayoutTabGroup(
                        identity = identity(23),
                        frame = rect(height = 100f),
                        contentFrame = rect(height = 100f),
                        title = "Tab",
                        children = inlineChildren("tab first paragraph", "tab second paragraph"),
                    )
                ),
            )
        ),
        first = "tab first paragraph",
        second = "tab second paragraph",
    )

    @Test
    fun directive_custom_content_children_are_stacked() = assertSecondBlockStartsBelowFirst(
        document = document(
            renderBlock(
                block = DirectiveBlockModel(
                    identity = identity(60),
                    tagName = "custom",
                    args = emptyMap(),
                    children = emptyList(),
                ),
                children = inlineChildren("custom directive first paragraph", "custom directive second paragraph"),
            )
        ),
        first = "custom directive first paragraph",
        second = "custom directive second paragraph",
        directiveRegistry = MarkdownDirectiveRegistry(
            listOf(
                object : MarkdownDirectivePlugin {
                    override val id: String = "test-custom-directive"
                    override val blockDirectiveRenderers: Map<String, MarkdownBlockDirectiveRenderer> = mapOf(
                        "custom" to { scope: DirectiveBlockRenderScope ->
                            Box {
                                scope.content?.invoke()
                            }
                        }
                    )
                }
            )
        ),
    )

    @Test
    fun directive_fallback_children_are_stacked() = assertSecondBlockStartsBelowFirst(
        document = document(
            renderBlock(
                block = DirectiveBlockModel(
                    identity = identity(30),
                    tagName = "note",
                    args = emptyMap(),
                    children = emptyList(),
                ),
                children = inlineChildren("directive first paragraph", "directive second paragraph"),
            )
        ),
        first = "directive first paragraph",
        second = "directive second paragraph",
    )

    @Test
    fun custom_container_children_are_stacked() = assertSecondBlockStartsBelowFirst(
        document = document(
            renderBlock(
                block = CustomContainerBlockModel(
                    identity = identity(40),
                    type = "note",
                    title = "",
                    cssClasses = emptyList(),
                    cssId = null,
                    children = emptyList(),
                ),
                children = inlineChildren("custom container first paragraph", "custom container second paragraph"),
            )
        ),
        first = "custom container first paragraph",
        second = "custom container second paragraph",
    )

    @Test
    fun fallback_container_children_are_stacked() = assertSecondBlockStartsBelowFirst(
        document = document(
            renderBlock(
                block = FallbackContainerBlockModel(
                    identity = identity(50),
                    children = emptyList(),
                ),
                children = inlineChildren("fallback first paragraph", "fallback second paragraph"),
            )
        ),
        first = "fallback first paragraph",
        second = "fallback second paragraph",
    )

    private fun assertSecondBlockStartsBelowFirst(
        document: InternalLayoutDocumentModel,
        first: String,
        second: String,
        directiveRegistry: MarkdownDirectiveRegistry = MarkdownDirectiveRegistry.Empty,
    ) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalMarkdownDirectiveRegistry provides directiveRegistry) {
                DefaultMarkdownComposePainter.Paint(
                    document = document,
                    environment = ComposeRenderEnvironment(
                        modifier = Modifier.width(320.dp),
                        renderMode = MarkdownRenderMode.StaticColumn,
                        enableScroll = false,
                    ),
                )
            }
        }

        waitForIdle()
        assertSecondTextBelowFirst(first, second)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertSecondTextBelowFirst(
        first: String,
        second: String,
    ) {
        val firstTop = onNodeWithText(first).topInRoot()
        val secondTop = onNodeWithText(second).topInRoot()

        assertTrue(
            actual = secondTop > firstTop,
            message = "Expected '$second' to start below '$first', but both started at y=$firstTop / $secondTop.",
        )
    }

    private fun SemanticsNodeInteraction.topInRoot(): Float = fetchSemanticsNode().boundsInRoot.top

    private fun document(vararg blocks: InternalLayoutBlockModel): InternalLayoutDocumentModel =
        InternalLayoutDocumentModel(
            identity = identity(1000),
            blocks = blocks.toList(),
            totalSize = LayoutSize(width = 320f, height = 200f),
        )

    private fun renderBlock(
        block: InternalRenderBlockModel,
        children: List<InternalLayoutBlockModel>,
    ): LayoutRenderBlockModel =
        LayoutRenderBlockModel(
            identity = block.identity,
            frame = rect(height = 100f),
            contentFrame = rect(height = 100f),
            block = block,
            children = children,
        )

    private fun inlineChildren(
        first: String,
        second: String,
    ): List<InternalLayoutBlockModel> =
        listOf(
            inlineBlock(id = 100, text = first),
            inlineBlock(id = 200, text = second),
        )

    private fun inlineBlock(id: Long, text: String): LayoutInlineBlockModel =
        LayoutInlineBlockModel(
            identity = identity(id),
            frame = rect(height = 20f),
            contentFrame = rect(height = 20f),
            style = TextStyle.Default,
            inlinePayloads = emptyMap(),
            lines = listOf(
                LayoutInlineLine(
                    frame = rect(height = 20f),
                    baseline = 16f,
                    runs = listOf(
                        LayoutTextRun(
                            identity = identity(id + 1),
                            frame = rect(width = 260f, height = 20f),
                            text = AnnotatedString(text),
                        )
                    ),
                )
            ),
        )

    private fun rect(
        left: Float = 0f,
        top: Float = 0f,
        width: Float = 320f,
        height: Float = 20f,
    ): LayoutRect = LayoutRect(left = left, top = top, width = width, height = height)

    private fun identity(id: Long): RenderIdentity =
        RenderIdentity(
            stableId = id,
            contentRevision = id,
            layoutRevision = id,
            paintRevision = id,
        )
}
