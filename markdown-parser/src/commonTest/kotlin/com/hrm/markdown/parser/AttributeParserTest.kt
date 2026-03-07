package com.hrm.markdown.parser

import com.hrm.markdown.parser.core.AttributeParser
import com.hrm.markdown.parser.core.Attributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttributeParserTest {

    @Test
    fun parse_single_class() {
        val (attrs, remaining) = AttributeParser.parse("kotlin {.highlight}")
        assertEquals(listOf("highlight"), attrs.classes)
        assertNull(attrs.id)
        assertTrue(attrs.pairs.isEmpty())
        assertEquals("kotlin", remaining)
    }

    @Test
    fun parse_multiple_classes() {
        val (attrs, remaining) = AttributeParser.parse("python {.line-numbers .dark-theme}")
        assertEquals(listOf("line-numbers", "dark-theme"), attrs.classes)
        assertEquals("python", remaining)
    }

    @Test
    fun parse_id() {
        val (attrs, remaining) = AttributeParser.parse("js {#my-block}")
        assertEquals("my-block", attrs.id)
        assertTrue(attrs.classes.isEmpty())
        assertEquals("js", remaining)
    }

    @Test
    fun parse_class_and_id() {
        val (attrs, remaining) = AttributeParser.parse("kotlin {.highlight #example}")
        assertEquals(listOf("highlight"), attrs.classes)
        assertEquals("example", attrs.id)
        assertEquals("kotlin", remaining)
    }

    @Test
    fun parse_unquoted_key_value() {
        val (attrs, _) = AttributeParser.parse("rust {linenos=true}")
        assertEquals("true", attrs.pairs["linenos"])
    }

    @Test
    fun parse_double_quoted_value() {
        val (attrs, _) = AttributeParser.parse("""python {title="my script"}""")
        assertEquals("my script", attrs.pairs["title"])
    }

    @Test
    fun parse_single_quoted_value() {
        val (attrs, _) = AttributeParser.parse("go {title='main.go'}")
        assertEquals("main.go", attrs.pairs["title"])
    }

    @Test
    fun parse_mixed_attrs() {
        val (attrs, remaining) = AttributeParser.parse("""json {.config #app data-line="3-5" readonly=true}""")
        assertEquals(listOf("config"), attrs.classes)
        assertEquals("app", attrs.id)
        assertEquals("3-5", attrs.pairs["data-line"])
        assertEquals("true", attrs.pairs["readonly"])
        assertEquals("json", remaining)
    }

    @Test
    fun parse_no_attrs() {
        val (attrs, remaining) = AttributeParser.parse("kotlin")
        assertTrue(attrs.isEmpty)
        assertEquals("kotlin", remaining)
    }

    @Test
    fun parse_empty_braces() {
        val (attrs, remaining) = AttributeParser.parse("kotlin {}")
        assertTrue(attrs.isEmpty)
        assertEquals("kotlin", remaining)
    }

    @Test
    fun parse_empty_input() {
        val (attrs, remaining) = AttributeParser.parse("")
        assertTrue(attrs.isEmpty)
        assertEquals("", remaining)
    }

    @Test
    fun parse_attrs_only() {
        val (attrs, remaining) = AttributeParser.parse("{.highlight #foo}")
        assertEquals(listOf("highlight"), attrs.classes)
        assertEquals("foo", attrs.id)
        assertEquals("", remaining)
    }

    @Test
    fun toMap_merges_correctly() {
        val attrs = Attributes(
            id = "block1",
            classes = listOf("a", "b"),
            pairs = mapOf("x" to "1"),
        )
        val map = attrs.toMap()
        assertEquals("block1", map["id"])
        assertEquals("a b", map["class"])
        assertEquals("1", map["x"])
    }

    @Test
    fun fenced_code_block_parses_attributes() {
        val parser = MarkdownParser()
        val doc = parser.parse("""
```kotlin {.highlight #demo linenos=true}
fun main() {}
```
        """.trimIndent())
        val codeBlock = doc.children.filterIsInstance<com.hrm.markdown.parser.ast.FencedCodeBlock>().first()
        assertEquals("kotlin", codeBlock.language)
        assertEquals(listOf("highlight"), codeBlock.attributes.classes)
        assertEquals("demo", codeBlock.attributes.id)
        assertEquals("true", codeBlock.attributes.pairs["linenos"])
    }

    @Test
    fun fenced_code_block_without_attrs_works() {
        val parser = MarkdownParser()
        val doc = parser.parse("""
```python
print("hello")
```
        """.trimIndent())
        val codeBlock = doc.children.filterIsInstance<com.hrm.markdown.parser.ast.FencedCodeBlock>().first()
        assertEquals("python", codeBlock.language)
        assertTrue(codeBlock.attributes.isEmpty)
    }
}
