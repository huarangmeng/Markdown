package com.hrm.markdown.renderer.internal.core.compile

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.TableHead
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.renderer.inline.InlinePlaceholderId
import com.hrm.markdown.renderer.internal.core.model.FallbackContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.FallbackLeafBlockModel
import com.hrm.markdown.renderer.internal.core.model.InlineMathWidgetModel
import com.hrm.markdown.renderer.internal.core.model.ParagraphBlockModel
import com.hrm.markdown.renderer.internal.core.model.WidgetAtom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultRenderModelCompilerTest {
    @Test
    fun should_compile_unknown_container_node_into_internal_fallback_container() {
        val document = Document().apply {
            appendChild(
                TableHead().apply {
                    appendChild(
                        Paragraph().apply {
                            appendChild(Text("fallback child"))
                        }
                    )
                }
            )
        }

        val renderDocument = DefaultRenderModelCompiler.compile(document, RenderCompileEnvironment())

        val fallback = assertIs<FallbackContainerBlockModel>(renderDocument.blocks.single())
        assertIs<ParagraphBlockModel>(fallback.children.single())
    }

    @Test
    fun should_compile_unknown_leaf_node_into_internal_fallback_leaf() {
        val document = Document().apply {
            appendChild(Text("orphan inline"))
        }

        val renderDocument = DefaultRenderModelCompiler.compile(document, RenderCompileEnvironment())

        assertIs<FallbackLeafBlockModel>(renderDocument.blocks.single())
    }

    @Test
    fun should_assign_distinct_placeholder_ids_to_multiple_inline_math_widgets() {
        val document = MarkdownParser().parse(
            "A battery does \$144\\text{ J}\$ of work with a potential difference of \$12\\text{ V}\$."
        )

        val renderDocument = DefaultRenderModelCompiler.compile(document, RenderCompileEnvironment())

        val paragraph = assertIs<ParagraphBlockModel>(renderDocument.blocks.single())
        val widgets = paragraph.inline.atoms
            .filterIsInstance<WidgetAtom>()
            .map { it.widget }
            .filterIsInstance<InlineMathWidgetModel>()
        val placeholderIds = widgets.map { InlinePlaceholderId.from(it) }

        assertEquals(listOf("144\\text{ J}", "12\\text{ V}"), widgets.map { it.latex })
        assertEquals(placeholderIds.size, placeholderIds.toSet().size)
    }

    @Test
    fun should_compile_numeric_answer_inline_math_as_widget() {
        val document = MarkdownParser().parse("\$12\\text{ C}\$")

        val renderDocument = DefaultRenderModelCompiler.compile(document, RenderCompileEnvironment())

        val paragraph = assertIs<ParagraphBlockModel>(renderDocument.blocks.single())
        val widget = paragraph.inline.atoms
            .filterIsInstance<WidgetAtom>()
            .map { it.widget }
            .single()

        assertIs<InlineMathWidgetModel>(widget)
        assertEquals("12\\text{ C}", widget.latex)
    }
}
