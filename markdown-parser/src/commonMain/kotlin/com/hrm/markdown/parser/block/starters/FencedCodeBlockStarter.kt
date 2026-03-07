package com.hrm.markdown.parser.block.starters

import com.hrm.markdown.parser.LineRange
import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.block.OpenBlock
import com.hrm.markdown.parser.core.AttributeParser
import com.hrm.markdown.parser.core.LineCursor

/**
 * 围栏代码块开启器：``` 或 ~~~。
 */
internal class FencedCodeBlockStarter : BlockStarter {
    override val priority: Int = 310
    override val canInterruptParagraph: Boolean = true

    override fun tryStart(cursor: LineCursor, lineIdx: Int, tip: OpenBlock): OpenBlock? {
        val indent = cursor.advanceSpaces(3)
        if (cursor.isAtEnd) return null

        val c = cursor.peek()
        if (c != '`' && c != '~') return null

        var fenceLength = 0
        while (!cursor.isAtEnd && cursor.peek() == c) {
            cursor.advance()
            fenceLength++
        }
        if (fenceLength < 3) return null

        val info = cursor.rest().trim()
        if (c == '`' && info.contains('`')) return null

        cursor.advance(cursor.remaining)

        // parse {.class #id key=value} from info-string, leave the rest for language extraction
        val (attributes, infoWithoutAttrs) = AttributeParser.parse(info)
        val language = infoWithoutAttrs.split(INFO_LANG_SPLIT_REGEX).firstOrNull()?.trim() ?: ""

        val block = FencedCodeBlock(
            info = info,
            language = language,
            fenceChar = c,
            fenceLength = fenceLength,
            fenceIndent = indent,
            attributes = attributes,
        )
        block.lineRange = LineRange(lineIdx, lineIdx + 1)

        val ob = OpenBlock(block, contentStartLine = lineIdx, lastLineIndex = lineIdx)
        ob.isFenced = true
        ob.fenceChar = c
        ob.fenceLength = fenceLength
        ob.fenceIndent = indent
        ob.starterTag = this::class.simpleName
        return ob
    }

    companion object {
        private val INFO_LANG_SPLIT_REGEX = Regex("\\s+")
    }
}
