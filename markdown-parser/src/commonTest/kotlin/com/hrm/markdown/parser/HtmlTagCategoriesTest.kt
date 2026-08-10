package com.hrm.markdown.parser

import com.hrm.markdown.parser.html.HtmlTagCategories
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlTagCategoriesTest {
    @Test
    fun should_keep_commonmark_type6_and_html_block_semantics_independent() {
        assertTrue(HtmlTagCategories.isCommonMarkType6BlockTag("base"))
        assertFalse(HtmlTagCategories.isHtmlBlockElement("base"))

        assertFalse(HtmlTagCategories.isCommonMarkType6BlockTag("pre"))
        assertTrue(HtmlTagCategories.isHtmlBlockElement("pre"))

        assertTrue(HtmlTagCategories.isCommonMarkType6BlockTag("div"))
        assertTrue(HtmlTagCategories.isHtmlBlockElement("div"))
    }
}
