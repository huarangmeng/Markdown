package com.hrm.markdown.parser

import com.hrm.markdown.parser.flavour.CommonMarkFlavour
import com.hrm.markdown.parser.html.HtmlRenderer
import com.hrm.markdown.parser.ast.*
import org.junit.Test

class DebugTest {
    @Test
    fun testTightList() {
        val md = "* a\n*\n\n* c\n"
        val parser = MarkdownParser(CommonMarkFlavour)
        val doc = parser.parse(md)
        val list = doc.children.firstOrNull()
        System.err.println("List node: ${list?.javaClass?.simpleName}")
        if (list is ListBlock) {
            System.err.println("List tight: ${list.tight}")
            for ((i, child) in list.children.withIndex()) {
                if (child is ListItem) {
                    System.err.println("Item $i: containsBlankLine=${child.containsBlankLine}, lineRange=${child.lineRange}")
                    for (grandchild in child.children) {
                        System.err.println("  -> ${grandchild.javaClass.simpleName} lineRange=${grandchild.lineRange}")
                    }
                }
            }
        }
        val html = HtmlRenderer().render(doc)
        System.err.println("HTML: ${html.replace("\n", "\\n")}")
        val debugFile = java.io.File("/tmp/debug-output.txt")
        debugFile.writeText(buildString {
            appendLine("List node: ${list?.javaClass?.simpleName}")
            if (list is ListBlock) {
                appendLine("List tight: ${list.tight}")
                for ((i, child) in list.children.withIndex()) {
                    if (child is ListItem) {
                        appendLine("Item $i: containsBlankLine=${child.containsBlankLine}, lineRange=${child.lineRange}")
                        for (grandchild in child.children) {
                            appendLine("  -> ${grandchild.javaClass.simpleName} lineRange=${grandchild.lineRange}")
                        }
                    }
                }
            }
            appendLine("HTML: ${html.replace("\n", "\\n")}")
        })
    }
}
