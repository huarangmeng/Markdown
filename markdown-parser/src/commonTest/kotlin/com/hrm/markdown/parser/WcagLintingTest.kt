package com.hrm.markdown.parser

import com.hrm.markdown.parser.lint.DiagnosticCode
import com.hrm.markdown.parser.lint.DiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * tests for wcag accessibility linting rules.
 */
class WcagLintingTest {

    private fun parseWithLinting(input: String): MarkdownParser {
        val parser = MarkdownParser(enableLinting = true)
        parser.parse(input)
        return parser
    }

    // ---- EMPTY_LINK_TEXT ----

    @Test
    fun should_detect_empty_link_text() {
        val parser = parseWithLinting("[](https://example.com)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.EMPTY_LINK_TEXT)
        assertEquals(1, issues.size)
        assertEquals(DiagnosticSeverity.WARNING, issues[0].severity)
    }

    @Test
    fun should_not_flag_link_with_text() {
        val parser = parseWithLinting("[Example](https://example.com)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.EMPTY_LINK_TEXT)
        assertEquals(0, issues.size)
    }

    // ---- LINK_TEXT_NOT_DESCRIPTIVE ----

    @Test
    fun should_detect_click_here_link_text() {
        val parser = parseWithLinting("[click here](https://example.com)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.LINK_TEXT_NOT_DESCRIPTIVE)
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("click here"))
    }

    @Test
    fun should_detect_here_link_text() {
        val parser = parseWithLinting("[here](https://example.com)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.LINK_TEXT_NOT_DESCRIPTIVE)
        assertEquals(1, issues.size)
    }

    @Test
    fun should_detect_read_more_link_text() {
        val parser = parseWithLinting("[read more](https://example.com)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.LINK_TEXT_NOT_DESCRIPTIVE)
        assertEquals(1, issues.size)
    }

    @Test
    fun should_not_flag_descriptive_link_text() {
        val parser = parseWithLinting("[Visit the documentation](https://example.com)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.LINK_TEXT_NOT_DESCRIPTIVE)
        assertEquals(0, issues.size)
    }

    @Test
    fun should_be_case_insensitive_for_non_descriptive_text() {
        val parser = parseWithLinting("[Click Here](https://example.com)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.LINK_TEXT_NOT_DESCRIPTIVE)
        assertEquals(1, issues.size)
    }

    // ---- MISSING_LANG_IN_CODE_BLOCK ----

    @Test
    fun should_detect_code_block_without_language() {
        val parser = parseWithLinting("```\nsome code\n```")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.MISSING_LANG_IN_CODE_BLOCK)
        assertEquals(1, issues.size)
        assertEquals(DiagnosticSeverity.INFO, issues[0].severity)
    }

    @Test
    fun should_not_flag_code_block_with_language() {
        val parser = parseWithLinting("```python\nsome code\n```")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.MISSING_LANG_IN_CODE_BLOCK)
        assertEquals(0, issues.size)
    }

    // ---- TABLE_MISSING_HEADER ----

    @Test
    fun should_detect_table_with_empty_headers() {
        val parser = parseWithLinting("""
| | |
|---|---|
| a | b |
        """.trimIndent())
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.TABLE_MISSING_HEADER)
        assertEquals(1, issues.size)
        assertEquals(DiagnosticSeverity.WARNING, issues[0].severity)
    }

    @Test
    fun should_not_flag_table_with_valid_headers() {
        val parser = parseWithLinting("""
| Name | Age |
|------|-----|
| Bob  | 30  |
        """.trimIndent())
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.TABLE_MISSING_HEADER)
        assertEquals(0, issues.size)
    }

    @Test
    fun should_not_flag_normal_length_alt_text() {
        val normalAlt = "A description of the image"
        val parser = parseWithLinting("![$normalAlt](image.png)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.LONG_ALT_TEXT)
        assertEquals(0, issues.size)
    }

    @Test
    fun should_allow_exactly_125_char_alt_text() {
        val alt = "a".repeat(125)
        val parser = parseWithLinting("![$alt](image.png)")
        val diagnostics = parser.diagnostics
        val issues = diagnostics.filter(DiagnosticCode.LONG_ALT_TEXT)
        assertEquals(0, issues.size)
    }

    // ---- combined checks ----

    @Test
    fun should_detect_multiple_wcag_issues() {
        val parser = parseWithLinting("""
[click here](https://example.com)

```
code without language
```

[](https://empty.com)
        """.trimIndent())
        val diagnostics = parser.diagnostics
        assertTrue(diagnostics.filter(DiagnosticCode.LINK_TEXT_NOT_DESCRIPTIVE).isNotEmpty())
        assertTrue(diagnostics.filter(DiagnosticCode.MISSING_LANG_IN_CODE_BLOCK).isNotEmpty())
        assertTrue(diagnostics.filter(DiagnosticCode.EMPTY_LINK_TEXT).isNotEmpty())
    }

    // ---- no false positives when linting disabled ----

    @Test
    fun should_not_produce_wcag_diagnostics_when_linting_disabled() {
        val parser = MarkdownParser(enableLinting = false)
        parser.parse("[click here](https://example.com)")
        val diagnostics = parser.diagnostics
        assertEquals(0, diagnostics.filter(DiagnosticCode.LINK_TEXT_NOT_DESCRIPTIVE).size)
    }
}
