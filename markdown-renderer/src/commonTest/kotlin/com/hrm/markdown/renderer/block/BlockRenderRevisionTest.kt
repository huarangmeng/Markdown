package com.hrm.markdown.renderer.block

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.MathBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BlockRenderRevisionTest {
    @Test
    fun should_distinguishMathBlocks_when_literalsHaveSameHashCode() {
        val first = MarkdownParser().parse("\$\$Aa\$\$").children.single() as MathBlock
        val second = MarkdownParser().parse("\$\$BB\$\$").children.single() as MathBlock

        assertEquals(first.literal.length, second.literal.length)
        assertEquals(first.literal.hashCode(), second.literal.hashCode())
        assertNotEquals(first.contentHash, second.contentHash)
        assertNotEquals(blockRenderRevision(first), blockRenderRevision(second))
    }
}
