package com.hrm.markdown.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownNavigationControllerTest {
    @Test
    fun should_offset_document_item_index_when_lazy_list_has_header() {
        assertEquals(4, resolveLazyDocumentItemIndex(documentIndex = 3, documentStartIndex = 1))
    }

    @Test
    fun should_keep_document_item_index_without_header() {
        assertEquals(3, resolveLazyDocumentItemIndex(documentIndex = 3, documentStartIndex = 0))
    }
}
