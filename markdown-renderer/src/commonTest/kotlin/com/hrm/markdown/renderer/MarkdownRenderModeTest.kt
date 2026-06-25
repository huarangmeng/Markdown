package com.hrm.markdown.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownRenderModeTest {
    @Test
    fun should_use_lazy_column_when_selection_is_enabled_and_scroll_is_enabled() {
        assertEquals(
            MarkdownRenderMode.LazyColumn,
            resolveMarkdownRenderMode(
                enableSelection = true,
                enableScroll = true,
                isStreaming = false,
            )
        )
    }

    @Test
    fun should_use_static_column_when_selection_is_enabled_without_internal_scroll() {
        assertEquals(
            MarkdownRenderMode.StaticColumn,
            resolveMarkdownRenderMode(
                enableSelection = true,
                enableScroll = false,
                isStreaming = false,
            )
        )
    }

    @Test
    fun should_use_lazy_column_when_selection_is_disabled_and_scroll_is_enabled() {
        assertEquals(
            MarkdownRenderMode.LazyColumn,
            resolveMarkdownRenderMode(
                enableSelection = false,
                enableScroll = true,
                isStreaming = false,
            )
        )
    }

    @Test
    fun should_use_static_column_when_selection_is_disabled_without_internal_scroll() {
        assertEquals(
            MarkdownRenderMode.StaticColumn,
            resolveMarkdownRenderMode(
                enableSelection = false,
                enableScroll = false,
                isStreaming = false,
            )
        )
    }

    @Test
    fun should_keep_streaming_on_static_column_even_when_selection_is_disabled() {
        assertEquals(
            MarkdownRenderMode.StaticColumn,
            resolveMarkdownRenderMode(
                enableSelection = false,
                enableScroll = true,
                isStreaming = true,
            )
        )
    }
}
