package com.hrm.markdown.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownPreviewTest {
    @Test
    fun should_keepAllPreviewItemsUnique_when_consolidatingNavigation() {
        val groups = previewCategories.flatMap { it.groups }
        val items = groups.flatMap { it.items }

        assertTrue(previewCategories.isNotEmpty())
        assertTrue(previewCategories.all { it.groups.isNotEmpty() })
        assertTrue(groups.all { it.items.isNotEmpty() })
        assertEquals(items.size, items.map { it.id }.toSet().size)
    }
}
