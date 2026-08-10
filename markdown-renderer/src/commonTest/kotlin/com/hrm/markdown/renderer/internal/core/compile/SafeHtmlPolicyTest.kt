package com.hrm.markdown.renderer.internal.core.compile

import com.hrm.markdown.parser.html.HtmlTagParser
import com.hrm.markdown.parser.html.HtmlTagToken
import com.hrm.markdown.renderer.internal.core.model.BlockTextAlignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SafeHtmlPolicyTest {
    @Test
    fun should_accept_only_attributes_that_are_fully_mapped() {
        assertIs<SafeInlineHtmlAction.Span>(inlineAction("<strong>"))
        assertNull(inlineAction("<strong title='not-mapped'>"))

        assertIs<SafeInlineHtmlAction.Span>(
            inlineAction("<span class='bold italic' style='color:#ff000080'>")
        )
        assertNull(inlineAction("<span class='unknown'>"))
        assertNull(inlineAction("<span style='position:fixed'>"))

        assertIs<SafeInlineHtmlAction.Image>(
            inlineAction("<img src='https://example.com/a.png' width='24Px'>")
        )
        assertNull(inlineAction("<img src='https://example.com/a.png' onerror='alert(1)'>"))
        assertNull(inlineAction("<a href='javascript:alert(1)'>"))
        assertNull(inlineAction("<a href='jav&#x61;script:alert(1)'>"))
    }

    @Test
    fun should_accept_only_fully_supported_block_alignment_attributes() {
        val action = assertNotNull(
            SafeHtmlPolicy.blockAction(
                token = tag("<div style='text-align:right'>"),
                inheritedAlignment = BlockTextAlignment.INHERIT,
            )
        )

        assertEquals(SafeBlockHtmlRole.CONTAINER, action.role)
        assertEquals(BlockTextAlignment.RIGHT, action.alignment)
        assertNull(
            SafeHtmlPolicy.blockAction(
                token = tag("<div style='text-align:center;color:red'>"),
                inheritedAlignment = BlockTextAlignment.INHERIT,
            )
        )
        assertNull(
            SafeHtmlPolicy.blockAction(
                token = tag("<div onclick='alert(1)'>"),
                inheritedAlignment = BlockTextAlignment.INHERIT,
            )
        )
    }

    private fun inlineAction(literal: String): SafeInlineHtmlAction? =
        SafeHtmlPolicy.inlineAction(tag(literal))

    private fun tag(literal: String): HtmlTagToken =
        assertNotNull(HtmlTagParser.parse(literal), literal)
}
