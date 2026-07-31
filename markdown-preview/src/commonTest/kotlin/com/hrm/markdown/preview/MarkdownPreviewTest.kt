package com.hrm.markdown.preview

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownPreviewTest {
    @Test
    fun should_keepAllPreviewItemsUnique_when_consolidatingNavigation() {
        val groups = previewCategories.flatMap { it.groups }
        val items = groups.flatMap { it.items }

        assertEquals(4, previewCategories.size)
        assertEquals(12, groups.size)
        assertEquals(148, items.size)
        assertEquals(items.size, items.map { it.id }.toSet().size)
    }
}
