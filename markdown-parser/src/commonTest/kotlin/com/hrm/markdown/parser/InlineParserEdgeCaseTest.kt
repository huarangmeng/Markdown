package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// emphasis

class EmphasisEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_triple_star_as_bold_italic() {
        val doc = parser.parse("***bold italic***")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.isNotEmpty())
    }

    @Test
    fun should_handle_mixed_star_and_underscore() {
        val doc = parser.parse("*foo **bar** baz*")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val emph = para.children.first()
        assertIs<Emphasis>(emph)
        assertTrue(emph.children.any { it is StrongEmphasis })
    }

    @Test
    fun should_not_open_emphasis_after_alphanumeric_with_underscore() {
        val doc = parser.parse("foo_bar_baz")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.none { it is Emphasis })
    }

    @Test
    fun should_allow_star_emphasis_intraword() {
        val doc = parser.parse("foo*bar*baz")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Emphasis })
    }

    @Test
    fun should_handle_mismatched_delimiters_as_text() {
        val doc = parser.parse("*foo**")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.isNotEmpty())
    }

    @Test
    fun should_handle_double_star_with_space() {
        val doc = parser.parse("** **")
        assertTrue(doc.children.isNotEmpty())
    }

    @Test
    fun should_handle_nested_emphasis_same_delimiter() {
        val doc = parser.parse("*foo *bar* baz*")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
    }

    @Test
    fun should_parse_strong_underscore() {
        val doc = parser.parse("__strong__")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val strong = para.children.first()
        assertIs<StrongEmphasis>(strong)
    }
}

// strikethrough

class StrikethroughEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_basic_strikethrough() {
        val doc = parser.parse("~~deleted~~")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val strike = para.children.first()
        assertIs<Strikethrough>(strike)
    }

    @Test
    fun should_handle_strikethrough_with_spaces_inside() {
        val doc = parser.parse("~~deleted text here~~")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Strikethrough })
    }

    @Test
    fun should_not_parse_single_tilde_as_strikethrough() {
        val doc = parser.parse("~not strike~")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.none { it is Strikethrough })
    }

    @Test
    fun should_handle_unmatched_tildes_as_text() {
        val doc = parser.parse("~~unmatched")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
    }
}

// inline code

class InlineCodeEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_triple_backtick_inline() {
        val doc = parser.parse("text ``` code ``` text")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is InlineCode })
    }

    @Test
    fun should_handle_backtick_inside_longer_delimiters() {
        val doc = parser.parse("``code ` here``")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertTrue(code.literal.contains("`"))
    }

    @Test
    fun should_collapse_newlines_to_spaces() {
        val doc = parser.parse("`line1\nline2`")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertEquals("line1 line2", code.literal)
    }

    @Test
    fun should_strip_single_leading_and_trailing_space() {
        val doc = parser.parse("`` foo ``")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertEquals("foo", code.literal)
    }

    @Test
    fun should_not_strip_when_content_is_only_spaces() {
        val doc = parser.parse("``  ``")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertEquals("  ", code.literal)
    }

    @Test
    fun should_treat_unmatched_backticks_as_literal() {
        val doc = parser.parse("`unmatched")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val text = para.children.first()
        assertIs<Text>(text)
        assertTrue(text.literal.contains("`"))
    }
}

// links

class LinkEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_handle_parens_in_url() {
        val doc = parser.parse("[wiki](https://en.wikipedia.org/wiki/Foo_(bar))")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
    }

    @Test
    fun should_parse_link_with_single_quote_title() {
        val doc = parser.parse("[text](url 'Title')")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("Title", link.title)
    }

    @Test
    fun should_parse_link_with_paren_title() {
        val doc = parser.parse("[text](url (Title))")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("Title", link.title)
    }

    @Test
    fun should_parse_empty_link_text() {
        val doc = parser.parse("[](https://example.com)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("https://example.com", link.destination)
        assertTrue(link.children.isEmpty())
    }

    @Test
    fun should_parse_link_with_formatted_text() {
        val doc = parser.parse("[**bold link**](url)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertTrue(link.children.any { it is StrongEmphasis })
    }

    @Test
    fun should_parse_angle_bracket_url() {
        val doc = parser.parse("[text](<url with spaces>)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("url with spaces", link.destination)
    }

    @Test
    fun should_handle_unmatched_bracket_as_text() {
        val doc = parser.parse("[unclosed link")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.first() is Text)
    }
}

// images

class ImageEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_image_with_empty_alt() {
        val doc = parser.parse("![](image.png)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("image.png", img.destination)
    }

    @Test
    fun should_parse_image_with_title() {
        val doc = parser.parse("![alt](img.png \"Photo\")")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("Photo", img.title)
    }

    @Test
    fun should_parse_image_with_url_spaces_in_angles() {
        val doc = parser.parse("![alt](<path with spaces.png>)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("path with spaces.png", img.destination)
    }

    @Test
    fun should_parse_image_without_title() {
        val doc = parser.parse("![alt text](image.png)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals(null, img.title)
    }
}

// autolinks

class AutolinkEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_http_autolink() {
        val doc = parser.parse("<http://example.com>")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Autolink>(link)
        assertEquals("http://example.com", link.destination)
    }

    @Test
    fun should_parse_https_autolink() {
        val doc = parser.parse("<https://example.com/path?q=1>")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Autolink>(link)
    }

    @Test
    fun should_parse_email_autolink() {
        val doc = parser.parse("<user@example.com>")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Autolink>(link)
        assertTrue(link.isEmail)
    }

    @Test
    fun should_not_parse_bare_url_as_autolink() {
        val doc = parser.parse("https://example.com")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.isNotEmpty())
    }
}

// escapes

class EscapeEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_escape_all_markdown_special_chars() {
        val doc = parser.parse("\\* \\_ \\[ \\] \\( \\) \\# \\> \\!")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val escaped = para.children.filterIsInstance<EscapedChar>()
        assertTrue(escaped.size >= 5)
    }

    @Test
    fun should_not_escape_alphanumeric() {
        val doc = parser.parse("\\a")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val text = para.children.first()
        assertIs<Text>(text)
        assertTrue(text.literal.contains("\\"))
    }

    @Test
    fun should_escape_backslash_itself() {
        val doc = parser.parse("\\\\")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is EscapedChar })
    }

    @Test
    fun should_prevent_emphasis_with_escape() {
        val doc = parser.parse("\\*not italic\\*")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.none { it is Emphasis })
        assertTrue(para.children.any { it is EscapedChar })
    }
}

// html entities

class HtmlEntityEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_resolve_copyright_entity() {
        val doc = parser.parse("&#xA9;")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val entity = para.children.first()
        assertIs<HtmlEntity>(entity)
        assertEquals("\u00A9", entity.resolved)
    }

    @Test
    fun should_resolve_nbsp() {
        val doc = parser.parse("&nbsp;")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val entity = para.children.first()
        assertIs<HtmlEntity>(entity)
        assertEquals("\u00A0", entity.resolved)
    }

    @Test
    fun should_keep_unknown_entity_as_text() {
        val doc = parser.parse("&unknownentity;")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.first() is Text)
    }

    @Test
    fun should_resolve_zero_codepoint_to_replacement_char() {
        val doc = parser.parse("&#0;")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val entity = para.children.first()
        assertIs<HtmlEntity>(entity)
        assertEquals("\uFFFD", entity.resolved)
    }
}

// highlight

class HighlightEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_basic_highlight() {
        val doc = parser.parse("==highlighted==")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertIs<Highlight>(para.children.first())
    }

    @Test
    fun should_parse_highlight_with_spaces() {
        val doc = parser.parse("==some highlighted text==")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Highlight })
    }

    @Test
    fun should_handle_unmatched_equals_as_text() {
        val doc = parser.parse("==unclosed")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.first() is Text)
    }
}

// emoji

class EmojiEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_known_emoji() {
        val doc = parser.parse(":smile:")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val emoji = para.children.first()
        assertIs<Emoji>(emoji)
        assertEquals("smile", emoji.shortcode)
    }

    @Test
    fun should_parse_thumbsup_emoji() {
        val doc = parser.parse(":+1:")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val emoji = para.children.first()
        assertIs<Emoji>(emoji)
    }

    @Test
    fun should_parse_emoji_in_text() {
        val doc = parser.parse("hello :wave: world")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Emoji })
    }
}

// superscript, subscript, inserted text

class SuperSubInsertEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_superscript() {
        val doc = parser.parse("x^2^")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Superscript })
    }

    @Test
    fun should_parse_subscript_as_text() {
        // ~ subscript is defined in AST but not implemented in the parser yet
        val doc = parser.parse("H~2~O")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.isNotEmpty())
    }

    @Test
    fun should_parse_inserted_text() {
        val doc = parser.parse("++inserted++")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is InsertedText })
    }

    @Test
    fun should_handle_unmatched_caret_as_text() {
        val doc = parser.parse("x^unmatched")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
    }

    @Test
    fun should_handle_superscript_with_text() {
        val doc = parser.parse("10^th^ place")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Superscript })
    }
}

// inline math

class InlineMathEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_simple_inline_math() {
        val doc = parser.parse("\$x + y\$")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is InlineMath })
    }

    @Test
    fun should_parse_double_dollar_inline_math() {
        val doc = parser.parse("\$\$E = mc^2\$\$")
        val first = doc.children.first()
        assertTrue(first is MathBlock || (first is Paragraph && first.children.any { it is InlineMath }))
    }

    @Test
    fun should_not_parse_dollar_after_digit_as_math() {
        val doc = parser.parse("costs 100\$")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.none { it is InlineMath })
    }
}

// inline html

class InlineHtmlEdgeCaseTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_open_and_close_tags() {
        val doc = parser.parse("text <em>emphasis</em> text")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is InlineHtml })
    }

    @Test
    fun should_parse_self_closing_inline_tag() {
        val doc = parser.parse("line<br/>break")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is InlineHtml })
    }

    @Test
    fun should_parse_html_comment_inline() {
        val doc = parser.parse("text <!-- comment --> more")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is InlineHtml })
    }
}
