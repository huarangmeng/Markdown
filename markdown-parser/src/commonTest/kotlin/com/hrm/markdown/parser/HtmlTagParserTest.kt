package com.hrm.markdown.parser

import com.hrm.markdown.parser.html.HtmlTagKind
import com.hrm.markdown.parser.html.HtmlTagParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlTagParserTest {
    @Test
    fun should_parse_structure_when_opening_tag_has_attributes() {
        val token = HtmlTagParser.parse("<SPAN Class='hero' DATA-ID=AbC disabled>")

        assertEquals(HtmlTagKind.OPENING, token?.kind)
        assertEquals("span", token?.name)
        assertEquals(
            mapOf(
                "class" to "hero",
                "data-id" to "AbC",
                "disabled" to "",
            ),
            token?.attributes,
        )
    }

    @Test
    fun should_classify_kind_when_tag_is_closing_or_self_closing() {
        val closing = HtmlTagParser.parse("</StRoNg  >")
        val selfClosing = HtmlTagParser.parse("<br class=\"clear\" />")

        assertEquals(HtmlTagKind.CLOSING, closing?.kind)
        assertEquals("strong", closing?.name)
        assertEquals(HtmlTagKind.SELF_CLOSING, selfClosing?.kind)
        assertEquals("br", selfClosing?.name)
        assertEquals(mapOf("class" to "clear"), selfClosing?.attributes)
    }

    @Test
    fun should_decode_entities_when_attribute_contains_html_reference() {
        val token = HtmlTagParser.parse("<a href='https://example.com?a=1&amp;b=2'>")

        assertEquals("https://example.com?a=1&b=2", token?.attributes?.get("href"))
    }

    @Test
    fun should_classify_kind_when_token_is_non_tag_html() {
        assertEquals(HtmlTagKind.COMMENT, HtmlTagParser.parse("<!-- hidden -->")?.kind)
        assertEquals(HtmlTagKind.CDATA, HtmlTagParser.parse("<![CDATA[value]]>")?.kind)
        assertEquals(HtmlTagKind.PROCESSING_INSTRUCTION, HtmlTagParser.parse("<?target value?>")?.kind)
        assertEquals(HtmlTagKind.DECLARATION, HtmlTagParser.parse("<!DOCTYPE html>")?.kind)
    }

    @Test
    fun should_reject_input_when_it_is_not_a_single_valid_tag() {
        assertNull(HtmlTagParser.parse("<strong>text</strong>"))
        assertNull(HtmlTagParser.parse("plain text"))
        assertNull(HtmlTagParser.parse("<broken attr=>"))
        assertNull(HtmlTagParser.parse("<!-- broken>"))
        assertNull(HtmlTagParser.parse("<?broken>"))
    }
}
