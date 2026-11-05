package com.hrm.markdown.renderer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownFootnoteNavigationTest {
    @Test
    fun should_preserveFootnoteNavigationAndReturn_when_observingInlineClicksInExternalScroll() = runComposeUiTest {
        lateinit var scroll: ScrollState
        var footnoteClicks = 0
        val source = "Reference[^note]\n\n" + (1..60).joinToString("\n\n") { "Filler paragraph $it." } + "\n\n[^note]: Footnote destination"
        setContent {
            scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Markdown(
                    markdown = source,
                    enableScroll = false,
                    externalScrollState = scroll,
                    interactions = MarkdownInteractions(onInlineElementClick = { event ->
                        if (event.kind == InlineElementKinds.Footnote) footnoteClicks++
                    }),
                )
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Reference", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Footnote destination").assertIsNotDisplayed()
        val positionBefore = scroll.value
        val reference = onAllNodes(
            hasText("[1]", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        )[0]
        val layouts = mutableListOf<TextLayoutResult>()
        reference.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        val text = layouts.single()
        val glyph = text.getBoundingBox(text.layoutInput.text.text.indexOf("[1]") + 1)
        reference.performTouchInput { click(glyph.center) }
        waitUntil(timeoutMillis = 5_000) { scroll.value > positionBefore }
        onNodeWithText("Footnote destination").assertIsDisplayed()
        assertEquals(1, footnoteClicks)
        onNodeWithText("↩").performClick()
        waitUntil(timeoutMillis = 5_000) { scroll.value == positionBefore }
        onNodeWithText("Reference", substring = true).assertIsDisplayed()
    }
}
