package com.hrm.markdown.parser

import com.hrm.markdown.parser.html.HtmlFragmentToken
import com.hrm.markdown.parser.html.HtmlFragmentTokenizer
import com.hrm.markdown.parser.html.HtmlTagKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HtmlFragmentTokenizerTest {
    @Test
    fun should_preserve_order_when_fragment_contains_nested_tags_and_text() {
        val tokens = HtmlFragmentTokenizer.tokenize(
            "<div align=\"center\">Hello <strong>Rohan</strong></div>"
        ).orEmpty()

        assertEquals(6, tokens.size)
        assertEquals("div", assertIs<HtmlFragmentToken.Tag>(tokens[0]).tag.name)
        assertEquals("Hello ", assertIs<HtmlFragmentToken.Text>(tokens[1]).literal)
        assertEquals("strong", assertIs<HtmlFragmentToken.Tag>(tokens[2]).tag.name)
        assertEquals("Rohan", assertIs<HtmlFragmentToken.Text>(tokens[3]).literal)
        assertEquals(HtmlTagKind.CLOSING, assertIs<HtmlFragmentToken.Tag>(tokens[4]).tag.kind)
        assertEquals(HtmlTagKind.CLOSING, assertIs<HtmlFragmentToken.Tag>(tokens[5]).tag.kind)
    }

    @Test
    fun should_keep_greater_than_when_it_is_inside_quoted_attribute() {
        val tokens = HtmlFragmentTokenizer.tokenize("<span title='1 > 0'>value</span>").orEmpty()
        val opening = assertIs<HtmlFragmentToken.Tag>(tokens.first())

        assertEquals("1 > 0", opening.tag.attributes["title"])
    }

    @Test
    fun should_keep_less_than_as_text_when_it_does_not_start_a_tag() {
        val tokens = HtmlFragmentTokenizer.tokenize("<div>1 < 2</div>").orEmpty()

        assertEquals("1 < 2", assertIs<HtmlFragmentToken.Text>(tokens[1]).literal)
    }

    @Test
    fun should_return_null_when_fragment_contains_unterminated_html_token() {
        assertNull(HtmlFragmentTokenizer.tokenize("<div title='broken>"))
        assertNull(HtmlFragmentTokenizer.tokenize("<!-- broken"))
    }
}
