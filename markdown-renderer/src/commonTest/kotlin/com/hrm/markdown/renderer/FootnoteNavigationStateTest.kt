package com.hrm.markdown.renderer

import androidx.compose.foundation.relocation.BringIntoViewRequester
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FootnoteNavigationStateTest {

    @Test
    fun should_register_definition_requester_for_label() {
        val state = FootnoteNavigationState()

        state.registerDefinition("note", BringIntoViewRequester())

        assertTrue(state.hasDefinition("note"))
    }

    @Test
    fun should_unregister_definition_requester_when_same_instance_removed() {
        val state = FootnoteNavigationState()
        val requester = BringIntoViewRequester()
        state.registerDefinition("note", requester)

        state.unregisterDefinition("note", requester)

        assertFalse(state.hasDefinition("note"))
    }

    @Test
    fun should_keep_latest_requester_when_stale_instance_is_unregistered() {
        val state = FootnoteNavigationState()
        val first = BringIntoViewRequester()
        val second = BringIntoViewRequester()
        state.registerDefinition("note", first)
        state.registerDefinition("note", second)

        state.unregisterDefinition("note", first)

        assertTrue(state.hasDefinition("note"))
    }

    @Test
    fun should_return_false_when_definition_requester_is_missing() = runBlocking {
        val state = FootnoteNavigationState()

        assertFalse(state.bringDefinitionIntoView("missing"))
    }

    @Test
    fun should_remember_return_position_for_label() {
        val state = FootnoteNavigationState()

        state.rememberReturnPosition("note", 128)

        assertTrue(state.hasReturnPosition("note"))
    }

    @Test
    fun should_overwrite_return_position_with_latest_scroll_value() {
        val state = FootnoteNavigationState()
        state.rememberReturnPosition("note", 64)

        state.rememberReturnPosition("note", 256)

        assertTrue(state.hasReturnPosition("note"))
        assertEquals(256, state.getReturnPosition("note"))
    }
}
