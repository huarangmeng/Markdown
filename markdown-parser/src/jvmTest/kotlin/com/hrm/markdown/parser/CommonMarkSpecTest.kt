package com.hrm.markdown.parser

import com.hrm.markdown.parser.flavour.CommonMarkFlavour
import com.hrm.markdown.parser.html.HtmlRenderer
import org.junit.Test

class CommonMarkSpecTest {

    data class SpecExample(
        val example: Int,
        val section: String,
        val markdown: String,
        val html: String,
    )

    private fun loadSpec(): List<SpecExample> {
        val lines = this::class.java.getResourceAsStream("/commonmark-spec.txt")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }
        return lines.map { line ->
            val parts = splitUnescaped(line, '|')
            check(parts.size >= 4) { "bad line: $line" }
            SpecExample(
                example = parts[0].toInt(),
                section = unescape(parts[1]),
                markdown = unescape(parts[2]),
                html = unescape(parts.drop(3).joinToString("|")),
            )
        }
    }

    private fun splitUnescaped(s: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                sb.append(s[i])
                sb.append(s[i + 1])
                i += 2
            } else if (s[i] == delimiter) {
                result.add(sb.toString())
                sb.clear()
                i++
            } else {
                sb.append(s[i])
                i++
            }
        }
        result.add(sb.toString())
        return result
    }

    // the txt file escapes \n \t \\ \|
    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (i + 1 < s.length && s[i] == '\\') {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '|' -> { sb.append('|'); i += 2 }
                    else -> { sb.append(s[i]); i++ }
                }
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    @Test
    fun runCommonMarkSpec() {
        val examples = loadSpec()
        check(examples.size >= 600) { "expected 600+ examples, got ${examples.size}" }

        var passed = 0
        var failed = 0
        val failedBySection = mutableMapOf<String, Int>()
        val failures = mutableListOf<String>()

        for (ex in examples) {
            val actual = HtmlRenderer.renderMarkdown(ex.markdown, flavour = CommonMarkFlavour)
            if (actual == ex.html) {
                passed++
            } else {
                failed++
                failedBySection[ex.section] = (failedBySection[ex.section] ?: 0) + 1
                failures.add(
                    "FAIL #${ex.example} [${ex.section}]\n" +
                    "  md:       ${ex.markdown.take(80).replace("\n", "\\n")}\n" +
                    "  expected: ${ex.html.take(120).replace("\n", "\\n")}\n" +
                    "  actual:   ${actual.take(120).replace("\n", "\\n")}"
                )
            }
        }

        val report = buildString {
            appendLine("=== CommonMark Spec 0.31.2 Results ===")
            appendLine("Total: ${examples.size}")
            appendLine("Passed: $passed (${passed * 100 / examples.size}%)")
            appendLine("Failed: $failed (${failed * 100 / examples.size}%)")
            appendLine()
            if (failedBySection.isNotEmpty()) {
                appendLine("Failures by section:")
                failedBySection.entries.sortedByDescending { it.value }.forEach { (section, count) ->
                    appendLine("  $section: $count")
                }
                appendLine()
            }
            if (failures.isNotEmpty()) {
                appendLine("All ${failures.size} failures:")
                failures.forEach { appendLine(it); appendLine() }
            }
        }
        java.io.File("/tmp/commonmark-results.txt").writeText(report)
        System.err.println(report)
    }
}
