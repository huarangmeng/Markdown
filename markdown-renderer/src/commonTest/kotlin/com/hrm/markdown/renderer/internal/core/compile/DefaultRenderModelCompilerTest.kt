package com.hrm.markdown.renderer.internal.core.compile

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.HtmlBlock
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.StyledText
import com.hrm.markdown.parser.ast.TableHead
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.renderer.inline.InlinePlaceholderId
import com.hrm.markdown.renderer.internal.core.model.FallbackContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.FallbackLeafBlockModel
import com.hrm.markdown.renderer.internal.core.model.BlockTextAlignment
import com.hrm.markdown.renderer.internal.core.model.HtmlBlockModel
import com.hrm.markdown.renderer.internal.core.model.HtmlContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.HtmlParagraphBlockModel
import com.hrm.markdown.renderer.internal.core.model.ImageWidgetModel
import com.hrm.markdown.renderer.internal.core.model.InlineMathWidgetModel
import com.hrm.markdown.renderer.internal.core.model.ListBlockModel
import com.hrm.markdown.renderer.internal.core.model.ParagraphBlockModel
import com.hrm.markdown.renderer.internal.core.model.TextAtom
import com.hrm.markdown.renderer.internal.core.model.WidgetAtom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DefaultRenderModelCompilerTest {
    @Test
    fun should_assign_unique_stable_ids_to_custom_ast_blocks_without_source_ranges() {
        val document = Document().apply {
            appendChild(Paragraph().apply { appendChild(Text("first")) })
            appendChild(Paragraph().apply { appendChild(Text("second")) })
        }

        val renderDocument = DefaultRenderModelCompiler.compile(document, RenderCompileEnvironment())

        assertEquals(2, renderDocument.blocks.map { it.identity.stableId }.toSet().size)
    }

    @Test
    fun should_keep_custom_ast_identity_stable_and_invalidate_semantic_changes() {
        fun document(text: String, color: String) = Document().apply {
            appendChild(
                Paragraph().apply {
                    appendChild(
                        StyledText(attributes = mapOf("style" to "color:$color")).apply {
                            appendChild(Text(text))
                        }
                    )
                }
            )
        }

        val before = DefaultRenderModelCompiler.compile(
            document("value", "red"),
            RenderCompileEnvironment(),
        ).blocks.single() as ParagraphBlockModel
        val afterStyle = DefaultRenderModelCompiler.compile(
            document("value", "blue"),
            RenderCompileEnvironment(),
        ).blocks.single() as ParagraphBlockModel
        val afterText = DefaultRenderModelCompiler.compile(
            document("updated", "red"),
            RenderCompileEnvironment(),
        ).blocks.single() as ParagraphBlockModel

        assertEquals(before.identity.stableId, afterStyle.identity.stableId)
        assertEquals(before.identity.stableId, afterText.identity.stableId)
        assertNotEquals(before.identity.contentRevision, afterStyle.identity.contentRevision)
        assertNotEquals(before.identity.contentRevision, afterText.identity.contentRevision)
        assertNotEquals(before.inline.identity.contentRevision, afterStyle.inline.identity.contentRevision)
        assertNotEquals(before.inline.identity.contentRevision, afterText.inline.identity.contentRevision)
    }

    @Test
    fun should_keep_active_streaming_tail_identity_when_only_its_end_grows() {
        val parser = MarkdownParser()
        val before = DefaultRenderModelCompiler.compile(
            parser.parse("stable\n\ntail"),
            RenderCompileEnvironment(),
        ).blocks
        val after = DefaultRenderModelCompiler.compile(
            parser.parse("stable\n\ntail updated"),
            RenderCompileEnvironment(),
        ).blocks

        assertEquals(before[1].identity.stableId, after[1].identity.stableId)
        val beforeParagraph = assertIs<ParagraphBlockModel>(before[1])
        val afterParagraph = assertIs<ParagraphBlockModel>(after[1])
        assertEquals(beforeParagraph.inline.identity.stableId, afterParagraph.inline.identity.stableId)
        assertEquals(
            beforeParagraph.inline.atoms.first().identity.stableId,
            afterParagraph.inline.atoms.first().identity.stableId,
        )
        assertNotEquals(before[1].identity.contentRevision, after[1].identity.contentRevision)
    }

    @Test
    fun should_namespace_inline_identities_by_their_parent_block() {
        val paragraphs = DefaultRenderModelCompiler.compile(
            MarkdownParser().parse("same\n\nsame"),
            RenderCompileEnvironment(),
        ).blocks.map { assertIs<ParagraphBlockModel>(it) }

        assertNotEquals(
            paragraphs[0].inline.identity.stableId,
            paragraphs[1].inline.identity.stableId,
        )
        assertNotEquals(
            paragraphs[0].inline.atoms.single().identity.stableId,
            paragraphs[1].inline.atoms.single().identity.stableId,
        )
    }

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

    @Test
    fun should_apply_nested_html_marks_when_tags_are_balanced() {
        val paragraph = compileParagraph("A <strong>bold <em>italic</em></strong>.")
        val textAtoms = paragraph.inline.atoms.filterIsInstance<TextAtom>()

        val bold = textAtoms.single { it.text == "bold " }
        val italic = textAtoms.single { it.text == "italic" }

        assertEquals(listOf("strong"), bold.marks.map { it.kind })
        assertEquals(listOf("strong", "emphasis"), italic.marks.map { it.kind })
    }

    @Test
    fun should_preserve_markdown_marks_when_nested_inside_html() {
        val paragraph = compileParagraph("<strong>*both*</strong>")
        val atom = paragraph.inline.atoms.filterIsInstance<TextAtom>().single()

        assertEquals("both", atom.text)
        assertEquals(listOf("strong", "emphasis"), atom.marks.map { it.kind })
    }

    @Test
    fun should_map_safe_span_and_anchor_attributes_when_supported() {
        val paragraph = compileParagraph(
            "<span style=\"color:red\" class='bold'>red</span> " +
                "<a href=\"https://example.com/path\">link</a>"
        )
        val textAtoms = paragraph.inline.atoms.filterIsInstance<TextAtom>()

        val styledMark = textAtoms.single { it.text == "red" }.marks.single()
        val linkMark = textAtoms.single { it.text == "link" }.marks.single()

        assertEquals("styled", styledMark.kind)
        assertEquals(mapOf("style" to "color:red", "class" to "bold"), styledMark.payload)
        assertEquals("link", linkMark.kind)
        assertEquals("https://example.com/path", linkMark.payload["target"])
    }

    @Test
    fun should_compile_html_break_comment_and_image_when_safe() {
        val paragraph = compileParagraph(
            "before<!-- hidden --><br><img src=\"https://example.com/a.png\" " +
                "alt='A' title='T' width=24 height=12>after"
        )
        val textAtoms = paragraph.inline.atoms.filterIsInstance<TextAtom>()
        val image = paragraph.inline.atoms
            .filterIsInstance<WidgetAtom>()
            .map { it.widget }
            .single()

        assertEquals(listOf("before", "\n", "after"), textAtoms.map { it.text })
        assertIs<ImageWidgetModel>(image)
        assertEquals("https://example.com/a.png", image.url)
        assertEquals("A", image.altText)
        assertEquals("T", image.title)
        assertEquals(24, image.width)
        assertEquals(12, image.height)
        assertEquals(setOf("src", "alt", "title", "width", "height"), image.attributes.keys)
    }

    @Test
    fun should_keep_source_when_html_is_unclosed_unknown_or_unsafe() {
        val inputs = listOf(
            "before <strong>unclosed",
            "<strong><em>nested unclosed</em>",
            "<strong><em>mismatched</strong></em>",
            "<custom>value</custom>",
            "<custom><!--visible fallback--><strong>nested</strong></custom>",
            "<strong onclick=\"alert(1)\">unsafe attribute</strong>",
            "<span class=\"unknown\">unsupported class</span>",
            "<span style=\"position:fixed\">unsupported CSS</span>",
            "before <img src=\"https://example.com/a.png\" onerror=\"alert(1)\">",
            "before <img src=\"https://example.com/a.png\"></img>",
            "<a href=\"javascript:alert(1)\">unsafe</a>",
            "<a href=\"jav&#x61;script:alert(1)\">encoded unsafe</a>",
        )

        for (input in inputs) {
            val paragraph = compileParagraph(input)
            val renderedText = paragraph.inline.atoms.filterIsInstance<TextAtom>().joinToString("") { it.text }
            assertEquals(input, renderedText)
            assertTrue(paragraph.inline.atoms.none { it is WidgetAtom })
            assertTrue(
                paragraph.inline.atoms
                    .filterIsInstance<TextAtom>()
                    .flatMap { it.marks }
                    .all { it.kind == "inline_html" },
                input,
            )
        }
    }

    @Test
    fun should_map_supported_html_aliases_when_balanced() {
        val cases = mapOf(
            "<b>x</b>" to "strong",
            "<i>x</i>" to "emphasis",
            "<s>x</s>" to "strikethrough",
            "<mark>x</mark>" to "highlight",
            "<sup>x</sup>" to "superscript",
            "<sub>x</sub>" to "subscript",
            "<ins>x</ins>" to "inserted",
            "<u>x</u>" to "underline",
            "<code>x</code>" to "html_code",
        )

        for ((input, expectedMark) in cases) {
            val atom = compileParagraph(input).inline.atoms.filterIsInstance<TextAtom>().single()
            assertEquals(expectedMark, atom.marks.single().kind, input)
        }
    }

    @Test
    fun should_compile_centered_html_div_when_fragment_is_safe() {
        val block = compileSingleBlock(
            "<div align=\"center\"><strong>[-NH-(CH2)6-NH-]</strong></div>"
        )
        val root = assertIs<HtmlContainerBlockModel>(block)
        val div = assertIs<HtmlContainerBlockModel>(root.children.single())
        val paragraph = assertIs<HtmlParagraphBlockModel>(div.children.single())
        val atom = paragraph.inline.atoms.filterIsInstance<TextAtom>().single()

        assertEquals(BlockTextAlignment.CENTER, paragraph.textAlignment)
        assertEquals("[-NH-(CH2)6-NH-]", atom.text)
        assertEquals("strong", atom.marks.single().kind)
    }

    @Test
    fun should_decode_entities_and_align_end_when_html_paragraph_is_safe() {
        val block = compileSingleBlock("<p align='right'>Rohan &amp; team</p>")
        val root = assertIs<HtmlContainerBlockModel>(block)
        val paragraph = assertIs<HtmlParagraphBlockModel>(root.children.single())

        assertEquals(BlockTextAlignment.RIGHT, paragraph.textAlignment)
        assertEquals("Rohan & team", paragraph.inline.atoms.filterIsInstance<TextAtom>().single().text)
    }

    @Test
    fun should_inherit_and_override_alignment_when_html_containers_are_nested() {
        val block = compileSingleBlock(
            "<div style=\"text-align:center\"><p>One</p><p align='left'>Two</p></div>"
        )
        val root = assertIs<HtmlContainerBlockModel>(block)
        val div = assertIs<HtmlContainerBlockModel>(root.children.single())
        val paragraphs = div.children.filterIsInstance<HtmlParagraphBlockModel>()

        assertEquals(listOf(BlockTextAlignment.CENTER, BlockTextAlignment.LEFT), paragraphs.map { it.textAlignment })
        assertEquals(listOf("One", "Two"), paragraphs.map { paragraph ->
            paragraph.inline.atoms.filterIsInstance<TextAtom>().joinToString("") { it.text }
        })
    }

    @Test
    fun should_not_parse_markdown_when_text_is_inside_html_block() {
        val block = compileSingleBlock("<div>**literal** <em>HTML emphasis</em></div>")
        val root = assertIs<HtmlContainerBlockModel>(block)
        val div = assertIs<HtmlContainerBlockModel>(root.children.single())
        val atoms = assertIs<HtmlParagraphBlockModel>(div.children.single()).inline.atoms.filterIsInstance<TextAtom>()

        assertEquals("**literal** HTML emphasis", atoms.joinToString("") { it.text })
        assertEquals("**literal**", atoms.first().text)
        assertTrue(atoms.first().marks.isEmpty())
        assertEquals("emphasis", atoms.last().marks.single().kind)
    }

    @Test
    fun should_compile_safe_html_unordered_list() {
        val block = compileSingleBlock("<ul>\n<li>Hello</li>\n<li><strong>World</strong></li>\n</ul>")
        val root = assertIs<HtmlContainerBlockModel>(block)
        val list = assertIs<ListBlockModel>(root.children.single())

        assertEquals(false, list.ordered)
        assertEquals(2, list.items.size)
        val first = assertIs<HtmlParagraphBlockModel>(list.items[0].children.single())
        val second = assertIs<HtmlParagraphBlockModel>(list.items[1].children.single())

        assertEquals("Hello", first.inline.atoms.filterIsInstance<TextAtom>().single().text)
        val secondAtom = second.inline.atoms.filterIsInstance<TextAtom>().single()
        assertEquals("World", secondAtom.text)
        assertEquals("strong", secondAtom.marks.single().kind)
    }

    @Test
    fun should_compile_safe_html_ordered_list_with_start_number() {
        val block = compileSingleBlock("<ol start=\"3\"><li>Alpha</li><li>Beta</li></ol>")
        val root = assertIs<HtmlContainerBlockModel>(block)
        val list = assertIs<ListBlockModel>(root.children.single())

        assertEquals(true, list.ordered)
        assertEquals(3, list.startNumber)
        assertEquals(listOf("Alpha", "Beta"), list.items.map { item ->
            assertIs<HtmlParagraphBlockModel>(item.children.single())
                .inline
                .atoms
                .filterIsInstance<TextAtom>()
                .joinToString("") { it.text }
        })
    }

    @Test
    fun should_compile_nested_safe_html_lists() {
        val block = compileSingleBlock(
            """
            <ul>
            <li>Parent
            <ol>
            <li>Child</li>
            </ol>
            </li>
            </ul>
            """.trimIndent()
        )
        val root = assertIs<HtmlContainerBlockModel>(block)
        val outer = assertIs<ListBlockModel>(root.children.single())
        val itemChildren = outer.items.single().children

        val parent = assertIs<HtmlParagraphBlockModel>(itemChildren[0])
        val inner = assertIs<ListBlockModel>(itemChildren[1])
        val child = assertIs<HtmlParagraphBlockModel>(inner.items.single().children.single())

        assertEquals("Parent", parent.inline.atoms.filterIsInstance<TextAtom>().single().text)
        assertEquals(true, inner.ordered)
        assertEquals("Child", child.inline.atoms.filterIsInstance<TextAtom>().single().text)
    }

    @Test
    fun should_keep_raw_html_block_when_fragment_is_malformed_or_unsupported() {
        val inputs = listOf(
            "<div><strong>broken</div>",
            "<table><tr><td>unsupported</td></tr></table>",
            "<custom>\nunknown type 7 block\n</custom>",
            "<div style=\"color:red\">unsupported block CSS</div>",
            "<div onclick=\"alert(1)\">unsupported block attribute</div>",
            "<div><a href=\"javascript:alert(1)\">unsafe child</a></div>",
            "<p><div>nested block must survive</div></p>",
            "<ul onclick=\"alert(1)\"><li>unsafe list</li></ul>",
            "<ul><li onclick=\"alert(1)\">unsafe item</li></ul>",
            "<ol start=\"x\"><li>bad start</li></ol>",
            "<li>orphan item</li>",
        )

        for (input in inputs) {
            val block = compileSingleBlock(input)
            assertEquals(input, assertIs<HtmlBlockModel>(block, input).html)
        }
    }

    @Test
    fun should_route_opaque_commonmark_html_block_types_directly_to_raw_fallback() {
        val cases = mapOf(
            1 to "<script><strong>must stay raw</strong>&amp;</script>",
            3 to "<?processing instruction?>",
            4 to "<!DECLARATION>",
            5 to "<![CDATA[<strong>must stay raw</strong>]]>",
        )

        for ((htmlType, input) in cases) {
            val document = Document().apply {
                appendChild(HtmlBlock(htmlType = htmlType, literal = input))
            }
            val block = DefaultRenderModelCompiler.compile(
                document = document,
                environment = RenderCompileEnvironment(),
            ).blocks.single()

            assertEquals(input, assertIs<HtmlBlockModel>(block).html, "HTML type $htmlType")
        }
    }

    @Test
    fun should_route_commonmark_html_comment_block_through_safe_hidden_semantics() {
        val document = Document().apply {
            appendChild(HtmlBlock(htmlType = 2, literal = "<!-- hidden -->"))
        }

        val block = DefaultRenderModelCompiler.compile(
            document = document,
            environment = RenderCompileEnvironment(),
        ).blocks.single()

        assertTrue(assertIs<HtmlContainerBlockModel>(block).children.isEmpty())
    }

    private fun compileParagraph(markdown: String): ParagraphBlockModel {
        return assertIs<ParagraphBlockModel>(compileSingleBlock(markdown))
    }

    private fun compileSingleBlock(markdown: String) = DefaultRenderModelCompiler.compile(
        document = MarkdownParser().parse(markdown),
        environment = RenderCompileEnvironment(),
    ).blocks.single()
}
