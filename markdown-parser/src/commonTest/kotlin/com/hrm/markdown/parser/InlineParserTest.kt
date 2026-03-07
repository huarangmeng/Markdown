package com.hrm.markdown.parser

import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.core.CharacterUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InlineParserTest {

    private val parser = MarkdownParser()

    // ────── Emphasis ──────

    @Test
    fun should_parse_star_emphasis() {
        val doc = parser.parse("*italic*")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val emph = para.children.first()
        assertIs<Emphasis>(emph)
        val text = emph.children.first()
        assertIs<Text>(text)
        assertEquals("italic", text.literal)
    }

    @Test
    fun should_parse_underscore_emphasis() {
        val doc = parser.parse("_italic_")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val emph = para.children.first()
        assertIs<Emphasis>(emph)
    }

    @Test
    fun should_parse_strong_emphasis() {
        val doc = parser.parse("**bold**")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val strong = para.children.first()
        assertIs<StrongEmphasis>(strong)
        val text = strong.children.first()
        assertIs<Text>(text)
        assertEquals("bold", text.literal)
    }

    @Test
    fun should_parse_double_underscore_strong() {
        val doc = parser.parse("__bold__")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val strong = para.children.first()
        assertIs<StrongEmphasis>(strong)
    }

    @Test
    fun should_parse_bold_italic() {
        val doc = parser.parse("***bold italic***")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // ***text*** 应该解析为嵌套的 StrongEmphasis + Emphasis（或反过来）
        // 外层应该是 Emphasis 或 StrongEmphasis
        val outer = para.children.first()
        assertTrue(
            outer is Emphasis || outer is StrongEmphasis,
            "Expected Emphasis or StrongEmphasis, got ${outer::class.simpleName}"
        )
        // 内层也应该是 Emphasis 或 StrongEmphasis
        val inner = (outer as ContainerNode).children.first()
        assertTrue(
            inner is Emphasis || inner is StrongEmphasis,
            "Expected nested Emphasis or StrongEmphasis, got ${inner::class.simpleName}"
        )
        // 最里层应该是文本 "bold italic"
        val text = (inner as ContainerNode).children.first()
        assertIs<Text>(text)
        assertEquals("bold italic", text.literal)
    }

    @Test
    fun should_parse_bold_inside_italic() {
        val doc = parser.parse("*italic **bold** italic*")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val emph = para.children.first()
        assertIs<Emphasis>(emph)
        // Should contain text, strong, text
        assertTrue(emph.children.size >= 1)
    }

    @Test
    fun should_not_parse_underscore_in_word() {
        val doc = parser.parse("foo_bar_baz")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // Should be plain text, not emphasis
        val text = para.children.first()
        assertIs<Text>(text)
        assertTrue(text.literal.contains("foo_bar_baz"))
    }

    // ────── Strikethrough ──────

    @Test
    fun should_parse_strikethrough() {
        val doc = parser.parse("~~deleted~~")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val strike = para.children.first()
        assertIs<Strikethrough>(strike)
    }

    @Test
    fun should_not_parse_triple_tilde_as_strikethrough() {
        // ~~~ at the start of a line is a fenced code block, not strikethrough
        val doc = parser.parse("~~~not strikethrough~~~")
        val first = doc.children.first()
        // This is a fenced code block per CommonMark spec (~~~ is a valid fence)
        assertIs<FencedCodeBlock>(first)
    }

    // ────── Inline Code ──────

    @Test
    fun should_parse_inline_code() {
        val doc = parser.parse("`code`")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertEquals("code", code.literal)
    }

    @Test
    fun should_parse_double_backtick_code() {
        val doc = parser.parse("``code with ` backtick``")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertTrue(code.literal.contains("`"))
    }

    @Test
    fun should_strip_single_space_from_code() {
        val doc = parser.parse("`` foo ``")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertEquals("foo", code.literal)
    }

    @Test
    fun should_not_strip_only_spaces() {
        val doc = parser.parse("``  ``")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertEquals("  ", code.literal)
    }

    @Test
    fun should_collapse_newlines_in_code() {
        val doc = parser.parse("`foo\nbar`")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val code = para.children.first()
        assertIs<InlineCode>(code)
        assertEquals("foo bar", code.literal)
    }

    @Test
    fun should_treat_unmatched_backticks_as_text() {
        val doc = parser.parse("`unmatched")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val text = para.children.first()
        assertIs<Text>(text)
        assertTrue(text.literal.contains("`"))
    }

    // ────── Links ──────

    @Test
    fun should_parse_inline_link() {
        val doc = parser.parse("[text](https://example.com)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("https://example.com", link.destination)
        val text = link.children.first()
        assertIs<Text>(text)
        assertEquals("text", text.literal)
    }

    @Test
    fun should_parse_link_with_title() {
        val doc = parser.parse("[text](url \"Title\")")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("Title", link.title)
    }

    @Test
    fun should_parse_link_with_angle_brackets() {
        val doc = parser.parse("[text](<url with spaces>)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("url%20with%20spaces", link.destination)
    }

    @Test
    fun should_parse_empty_url_link() {
        val doc = parser.parse("[text]()")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("", link.destination)
    }

    @Test
    fun should_parse_autolink() {
        val doc = parser.parse("<https://example.com>")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Autolink>(link)
        assertEquals("https://example.com", link.destination)
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

    // ────── Images ──────

    @Test
    fun should_parse_image() {
        val doc = parser.parse("![alt text](image.png)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("image.png", img.destination)
    }

    @Test
    fun should_parse_image_with_title() {
        val doc = parser.parse("![alt](img.png \"Title\")")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("Title", img.title)
    }

    // ────── Escapes ──────

    @Test
    fun should_parse_escaped_character() {
        val doc = parser.parse("\\*not italic\\*")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // Should contain escaped chars and text, not emphasis
        assertTrue(para.children.any { it is EscapedChar })
    }

    @Test
    fun should_parse_backslash_hard_break() {
        val doc = parser.parse("line1\\\nline2")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is HardLineBreak })
    }

    // ────── HTML Entities ──────

    @Test
    fun should_parse_named_entity() {
        val doc = parser.parse("&amp;")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val entity = para.children.first()
        assertIs<HtmlEntity>(entity)
        assertEquals("&", entity.resolved)
    }

    @Test
    fun should_parse_decimal_entity() {
        val doc = parser.parse("&#123;")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val entity = para.children.first()
        assertIs<HtmlEntity>(entity)
        assertEquals("{", entity.resolved)
    }

    @Test
    fun should_parse_hex_entity() {
        val doc = parser.parse("&#xA9;")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val entity = para.children.first()
        assertIs<HtmlEntity>(entity)
        assertEquals("\u00A9", entity.resolved)
    }

    // ────── Line Breaks ──────

    @Test
    fun should_parse_hard_line_break_with_spaces() {
        val doc = parser.parse("line1  \nline2")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is HardLineBreak })
    }

    @Test
    fun should_parse_soft_line_break() {
        val doc = parser.parse("line1\nline2")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is SoftLineBreak })
    }

    // ────── Inline HTML ──────

    @Test
    fun should_parse_inline_html_tag() {
        val doc = parser.parse("text <em>emphasis</em> text")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is InlineHtml })
    }

    // ────── Inline Math ──────

    @Test
    fun should_parse_inline_math() {
        val doc = parser.parse("The formula \$E = mc^2\$ is famous")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is InlineMath })
    }

    @Test
    fun should_not_parse_dollar_after_digit() {
        val doc = parser.parse("Price is 100\$")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // Should not be parsed as math
        assertTrue(para.children.none { it is InlineMath })
    }

    // ────── Highlight ──────

    @Test
    fun should_parse_highlight() {
        val doc = parser.parse("==highlighted==")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val hl = para.children.first()
        assertIs<Highlight>(hl)
    }

    // ────── Emoji ──────

    @Test
    fun should_parse_emoji_shortcode() {
        val doc = parser.parse(":smile:")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val emoji = para.children.first()
        assertIs<Emoji>(emoji)
        assertEquals("smile", emoji.shortcode)
    }

    @Test
    fun should_not_parse_invalid_emoji() {
        val doc = parser.parse(":not_a_real_emoji_xyz:")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
    }

    // ────── Superscript / Subscript / InsertedText ──────

    @Test
    fun should_parse_superscript() {
        val doc = parser.parse("x^2^")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is Superscript })
    }

    @Test
    fun should_parse_subscript() {
        val doc = parser.parse("H~2~O")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // 验证下标节点被正确创建
        assertTrue(para.children.any { it is Subscript })
        val sub = para.children.filterIsInstance<Subscript>().first()
        val text = sub.children.first()
        assertIs<Text>(text)
        assertEquals("2", text.literal)
    }

    @Test
    fun should_parse_inserted_text() {
        val doc = parser.parse("++inserted++")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val ins = para.children.first()
        assertIs<InsertedText>(ins)
    }

    // ────── Footnote Reference ──────

    @Test
    fun should_parse_footnote_definition_and_reference() {
        val input = "[^1]: This is footnote.\n\nText with [^1]."
        val doc = parser.parse(input)
        assertTrue(doc.children.isNotEmpty())
        val para = doc.children.filterIsInstance<Paragraph>().firstOrNull()
        assertIs<Paragraph>(para)
        // 验证脚注引用节点被正确创建
        assertTrue(para.children.any { it is FootnoteReference })
        val ref = para.children.filterIsInstance<FootnoteReference>().first()
        assertEquals("1", ref.label)
        assertTrue(ref.index > 0)
    }

    @Test
    fun should_parse_footnote_reference_without_definition() {
        val doc = parser.parse("Text with [^unknown].")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // 即使没有定义也创建 FootnoteReference 节点（index=0）
        assertTrue(para.children.any { it is FootnoteReference })
        val ref = para.children.filterIsInstance<FootnoteReference>().first()
        assertEquals("unknown", ref.label)
        assertEquals(0, ref.index)
    }

    // ────── Link Edge Cases ──────

    @Test
    fun should_parse_link_with_parentheses_in_url() {
        val doc = parser.parse("[wiki](https://en.wikipedia.org/wiki/Markdown_(software))")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // Should at least parse the link or text
        assertTrue(para.children.isNotEmpty())
    }

    @Test
    fun should_parse_link_with_empty_text() {
        val doc = parser.parse("[](https://example.com)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("https://example.com", link.destination)
    }

    // ────── Emphasis Edge Cases ──────

    @Test
    fun should_not_parse_unmatched_asterisk_as_emphasis() {
        val doc = parser.parse("This has * a single asterisk")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.none { it is Emphasis })
    }

    @Test
    fun should_parse_emphasis_with_spaces_inside() {
        val doc = parser.parse("* not emphasis * but a list")
        val first = doc.children.first()
        // This starts with "* " so it's a list, not emphasis
        assertIs<ListBlock>(first)
    }
}

// ────── 链接引用定义标题跨行 ──────

class LinkRefMultilineTitleTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_single_line_link_ref() {
        val input = "[foo]: /url \"Title\"\n\n[foo]"
        val doc = parser.parse(input)
        assertTrue(doc.linkDefinitions.containsKey("foo"))
        assertEquals("/url", doc.linkDefinitions["foo"]!!.destination)
        assertEquals("Title", doc.linkDefinitions["foo"]!!.title)
    }

    @Test
    fun should_parse_multiline_title_link_ref() {
        val input = "[foo]: /url\n  \"Title\"\n\n[foo]"
        val doc = parser.parse(input)
        assertTrue(doc.linkDefinitions.containsKey("foo"))
        assertEquals("/url", doc.linkDefinitions["foo"]!!.destination)
        assertEquals("Title", doc.linkDefinitions["foo"]!!.title)
    }

    @Test
    fun should_parse_link_ref_with_angle_bracket_destination() {
        val input = "[foo]: <https://example.com> \"Title\"\n\n[foo]"
        val doc = parser.parse(input)
        assertTrue(doc.linkDefinitions.containsKey("foo"))
    }

    @Test
    fun should_parse_link_ref_without_title() {
        val input = "[bar]: /url\n\n[bar]"
        val doc = parser.parse(input)
        assertTrue(doc.linkDefinitions.containsKey("bar"))
        assertEquals("/url", doc.linkDefinitions["bar"]!!.destination)
    }

    @Test
    fun should_parse_link_ref_case_insensitive() {
        val input = "[FOO]: /url\n\n[foo]"
        val doc = parser.parse(input)
        assertTrue(doc.linkDefinitions.containsKey("foo"))
    }
}

// ────── 键盘按键 ──────

class KeyboardInputTest {

    private val parser = MarkdownParser()

    @Test
    fun should_parse_kbd_tag() {
        val doc = parser.parse("Press <kbd>Ctrl</kbd> to continue")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        assertTrue(para.children.any { it is KeyboardInput })
        val kbd = para.children.filterIsInstance<KeyboardInput>().first()
        assertEquals("Ctrl", kbd.literal)
    }

    @Test
    fun should_parse_multiple_kbd_tags() {
        val doc = parser.parse("Press <kbd>Ctrl</kbd>+<kbd>C</kbd> to copy")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val kbds = para.children.filterIsInstance<KeyboardInput>()
        assertEquals(2, kbds.size)
        assertEquals("Ctrl", kbds[0].literal)
        assertEquals("C", kbds[1].literal)
    }

    @Test
    fun should_handle_empty_kbd() {
        val doc = parser.parse("Key: <kbd></kbd> pressed")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val kbd = para.children.filterIsInstance<KeyboardInput>().first()
        assertEquals("", kbd.literal)
    }

    @Test
    fun should_parse_kbd_with_special_key() {
        val doc = parser.parse("Press <kbd>Enter</kbd>")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val kbd = para.children.filterIsInstance<KeyboardInput>().first()
        assertEquals("Enter", kbd.literal)
    }
}

// ────── URL 百分号编码 ──────

class UrlPercentEncodingTest {

    @Test
    fun should_encode_space_in_url() {
        val result = com.hrm.markdown.parser.core.CharacterUtils.percentEncodeUrl("https://example.com/my page")
        assertEquals("https://example.com/my%20page", result)
    }

    @Test
    fun should_preserve_existing_encoding() {
        val result = com.hrm.markdown.parser.core.CharacterUtils.percentEncodeUrl("https://example.com/my%20page")
        assertEquals("https://example.com/my%20page", result)
    }

    @Test
    fun should_not_encode_safe_chars() {
        val result = com.hrm.markdown.parser.core.CharacterUtils.percentEncodeUrl("https://example.com/path?q=1&r=2#hash")
        assertEquals("https://example.com/path?q=1&r=2#hash", result)
    }

    @Test
    fun should_encode_chinese_chars() {
        val result = com.hrm.markdown.parser.core.CharacterUtils.percentEncodeUrl("https://example.com/你好")
        assertTrue(result.startsWith("https://example.com/"))
        assertTrue(result.contains("%"))
    }

    @Test
    fun should_apply_percent_encoding_in_link() {
        val parser = MarkdownParser()
        val doc = parser.parse("[link](<https://example.com/my page>)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertEquals("https://example.com/my%20page", link.destination)
    }

    @Test
    fun should_encode_bare_url_in_link() {
        val parser = MarkdownParser()
        val doc = parser.parse("[link](https://example.com/path%20ok)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val link = para.children.first()
        assertIs<Link>(link)
        assertTrue(link.destination.contains("%20"))
    }

    @Test
    fun should_handle_empty_url() {
        val result = com.hrm.markdown.parser.core.CharacterUtils.percentEncodeUrl("")
        assertEquals("", result)
    }
}

// ────── 图片高级特性 ──────

class ImageAdvancedFeatureTest {

    private val parser = MarkdownParser()

    // ────── =WxH 尺寸语法 ──────

    @Test
    fun should_parse_image_with_width_and_height() {
        val doc = parser.parse("![alt](image.png =200x300)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("image.png", img.destination)
        assertEquals(200, img.imageWidth)
        assertEquals(300, img.imageHeight)
    }

    @Test
    fun should_parse_image_with_width_only() {
        val doc = parser.parse("![alt](image.png =200x)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals(200, img.imageWidth)
        assertNull(img.imageHeight)
    }

    @Test
    fun should_parse_image_with_height_only() {
        val doc = parser.parse("![alt](image.png =x300)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertNull(img.imageWidth)
        assertEquals(300, img.imageHeight)
    }

    @Test
    fun should_parse_image_with_size_and_title() {
        val doc = parser.parse("![alt](image.png =200x300 \"My Title\")")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("image.png", img.destination)
        assertEquals(200, img.imageWidth)
        assertEquals(300, img.imageHeight)
        assertEquals("My Title", img.title)
    }

    @Test
    fun should_parse_image_without_size() {
        val doc = parser.parse("![alt](image.png)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("image.png", img.destination)
        assertNull(img.imageWidth)
        assertNull(img.imageHeight)
    }

    @Test
    fun should_not_parse_size_for_links() {
        val doc = parser.parse("[text](url =200x300)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // 链接不支持 =WxH 语法，应该解析失败作为纯文本
        val children = para.children
        assertFalse(children.any { it is Link })
    }

    // ────── {attribute} 属性块语法 ──────

    @Test
    fun should_parse_image_with_css_class() {
        val doc = parser.parse("![alt](image.png){.rounded}")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("image.png", img.destination)
        assertTrue(img.cssClasses.contains("rounded"))
    }

    @Test
    fun should_parse_image_with_css_id() {
        val doc = parser.parse("![alt](image.png){#hero-image}")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("hero-image", img.cssId)
    }

    @Test
    fun should_parse_image_with_multiple_classes_and_id() {
        val doc = parser.parse("![alt](image.png){.rounded .shadow #img1}")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertTrue(img.cssClasses.contains("rounded"))
        assertTrue(img.cssClasses.contains("shadow"))
        assertEquals("img1", img.cssId)
    }

    @Test
    fun should_parse_image_with_key_value_attributes() {
        val doc = parser.parse("![alt](image.png){loading=lazy align=right}")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("lazy", img.attributes["loading"])
        assertEquals("right", img.attributes["align"])
    }

    @Test
    fun should_parse_image_with_quoted_attribute_value() {
        val doc = parser.parse("![alt](image.png){loading=\"lazy\" title=\"My Image\"}")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("lazy", img.attributes["loading"])
        assertEquals("My Image", img.attributes["title"])
    }

    @Test
    fun should_parse_image_with_size_and_attributes() {
        val doc = parser.parse("![alt](image.png =200x300){.rounded loading=lazy}")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals(200, img.imageWidth)
        assertEquals(300, img.imageHeight)
        assertTrue(img.cssClasses.contains("rounded"))
        assertEquals("lazy", img.attributes["loading"])
    }

    @Test
    fun should_parse_image_with_size_title_and_attributes() {
        val doc = parser.parse("![alt](image.png =200x300 \"Title\"){.rounded}")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals(200, img.imageWidth)
        assertEquals(300, img.imageHeight)
        assertEquals("Title", img.title)
        assertTrue(img.cssClasses.contains("rounded"))
    }

    @Test
    fun should_handle_image_without_attributes() {
        val doc = parser.parse("![alt](image.png)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertTrue(img.attributes.isEmpty())
    }

    // ────── 边界情况 ──────

    @Test
    fun should_handle_image_with_url_containing_equals() {
        // URL 中包含 = 但不是尺寸语法（没有 x 分隔符）
        val doc = parser.parse("![alt](https://example.com/img?w=100)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        // URL 参数中的 = 属于 URL 的一部分，不应被解析为尺寸
        assertNull(img.imageWidth)
        assertNull(img.imageHeight)
    }

    @Test
    fun should_handle_incomplete_attribute_block() {
        // 未关闭的 { 不应解析为属性
        val doc = parser.parse("![alt](image.png){.rounded")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertTrue(img.attributes.isEmpty())
    }

    @Test
    fun should_parse_image_with_angle_bracket_url_and_size() {
        val doc = parser.parse("![alt](<https://example.com/img.png> =200x300)")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("https://example.com/img.png", img.destination)
        assertEquals(200, img.imageWidth)
        assertEquals(300, img.imageHeight)
    }

    @Test
    fun should_parse_image_with_single_quote_attributes() {
        val doc = parser.parse("![alt](image.png){loading='lazy'}")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        val img = para.children.first()
        assertIs<Image>(img)
        assertEquals("lazy", img.attributes["loading"])
    }
}

// ────── 按需内联解析（Lazy Inline Parsing）测试 ──────

class LazyInlineParsingTest {

    private val parser = MarkdownParser()

    @Test
    fun should_lazy_parse_paragraph_inline_content() {
        val doc = parser.parse("Hello **bold** world")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // 首次访问 children 触发行内解析
        assertTrue(para.children.isNotEmpty())
        val strong = para.children[1]
        assertIs<StrongEmphasis>(strong)
    }

    @Test
    fun should_lazy_parse_heading_inline_content() {
        val doc = parser.parse("# Hello *italic*")
        val heading = doc.children.first()
        assertIs<Heading>(heading)
        assertTrue(heading.children.isNotEmpty())
        val em = heading.children[1]
        assertIs<Emphasis>(em)
    }

    @Test
    fun should_parse_table_cells_lazily() {
        val doc = parser.parse("| **A** | *B* |\n| --- | --- |\n| 1 | 2 |")
        val table = doc.children.first()
        assertIs<Table>(table)
        val head = table.children.first()
        assertIs<TableHead>(head)
        val headerRow = head.children.first()
        assertIs<TableRow>(headerRow)
        val cell = headerRow.children.first()
        assertIs<TableCell>(cell)
        // 表头单元格包含行内内容
        assertTrue(cell.children.isNotEmpty())
    }

    @Test
    fun should_mark_inline_as_parsed_after_access() {
        val doc = parser.parse("Hello **bold**")
        val para = doc.children.first()
        assertIs<Paragraph>(para)
        // 首次访问触发解析
        para.children
        assertTrue(para.isInlineParsed)
    }
}
