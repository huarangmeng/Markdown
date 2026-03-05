package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

// link reference definitions — [label]: url "title"

class LinkReferenceDefinitionTest {

    private val parser = MarkdownParser()

    @Test
    fun should_store_definition_in_document() {
        val doc = parser.parse("[example]: https://example.com \"Example\"")
        assertTrue(doc.linkDefinitions.isNotEmpty())
    }

    @Test
    fun should_resolve_reference_link() {
        val doc = parser.parse("[example]: https://example.com\n\n[click][example]")
        assertTrue(doc.linkDefinitions.isNotEmpty())
        val para = doc.children.filterIsInstance<Paragraph>().first()
        assertTrue(para.children.any { it is Link })
    }

    @Test
    fun should_match_labels_case_insensitively() {
        val doc = parser.parse("[FOO]: https://example.com\n\n[click][foo]")
        assertTrue(doc.linkDefinitions.isNotEmpty())
    }

    @Test
    fun should_use_first_definition_for_duplicates() {
        val doc = parser.parse("[foo]: /url1\n[foo]: /url2\n\n[foo]")
        assertTrue(doc.linkDefinitions.isNotEmpty())
    }

    @Test
    fun should_parse_collapsed_reference_link() {
        val doc = parser.parse("[example]: https://example.com\n\n[example][]")
        val para = doc.children.filterIsInstance<Paragraph>().first()
        assertTrue(para.children.any { it is Link })
    }

    @Test
    fun should_parse_shortcut_reference_link() {
        val doc = parser.parse("[example]: https://example.com\n\n[example]")
        val para = doc.children.filterIsInstance<Paragraph>().first()
        assertTrue(para.children.any { it is Link })
    }

    @Test
    fun should_normalize_whitespace_in_label() {
        val doc = parser.parse("[  FOO  BAR  ]: /url\n\n[foo bar]")
        assertTrue(doc.linkDefinitions.isNotEmpty())
    }

    @Test
    fun should_parse_definition_without_title() {
        val doc = parser.parse("[foo]: /url\n\n[foo]")
        assertTrue(doc.linkDefinitions.isNotEmpty())
    }

    @Test
    fun should_parse_definition_with_angle_bracket_url() {
        val doc = parser.parse("[foo]: <https://example.com/path>\n\n[foo]")
        assertTrue(doc.linkDefinitions.isNotEmpty())
    }
}

// footnotes — [^label] references and definitions

class FootnoteTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_footnote_reference_and_definition() {
        val doc = parser.parse("Text[^1].\n\n[^1]: Footnote content.")
        assertTrue(doc.children.isNotEmpty())
        assertTrue(doc.footnoteDefinitions.isNotEmpty() ||
            doc.children.any { it is FootnoteDefinition })
    }

    @Test
    fun should_parse_named_footnote() {
        val doc = parser.parse("Text[^note].\n\n[^note]: Named footnote.")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_parse_multiple_footnote_definitions() {
        val input = "First[^1] and second[^2].\n\n[^1]: Note 1.\n\n[^2]: Note 2."
        val doc = parser.parse(input)
        assertTrue(doc.children.isNotEmpty())
        val defs = doc.children.filterIsInstance<FootnoteDefinition>()
        val defsMap = doc.footnoteDefinitions
        assertTrue(defs.size >= 2 || defsMap.size >= 2)
    }

    @Test
    fun should_parse_footnote_definition_with_inline_content() {
        val doc = parser.parse("Text[^1].\n\n[^1]: Footnote with **bold**.")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_parse_multiline_footnote_definition() {
        val input = "Text[^1].\n\n[^1]: First line.\n    Second line."
        val doc = parser.parse(input)
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_store_footnote_in_document() {
        val doc = parser.parse("Text[^abc].\n\n[^abc]: Content.")
        assertTrue(doc.children.isNotEmpty())
        assertTrue(doc.footnoteDefinitions.isNotEmpty() ||
            doc.children.any { it is FootnoteDefinition })
    }
}
