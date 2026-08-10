package com.hrm.markdown.renderer.block

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.ProvideMarkdownTheme
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.core.model.FallbackLeafBlockModel
import com.hrm.markdown.renderer.internal.core.model.TabBlockModel
import com.hrm.markdown.renderer.internal.core.model.TabItemBlockModel
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TabBlockRendererTest {
    @Test
    fun should_keep_selected_tab_by_identity_when_items_reorder() = runComposeUiTest {
        val first = tab(id = 1, title = "First", contentId = 101)
        val second = tab(id = 2, title = "Second", contentId = 102)
        var items by mutableStateOf(listOf(first, second))

        setContent {
            ProvideMarkdownTheme(MarkdownTheme()) {
                RenderTabBlockModel(
                    model = TabBlockModel(identity(10), items),
                    renderChildren = { children ->
                        Text(if (children.single().identity.stableId == 101L) "first content" else "second content")
                    },
                )
            }
        }

        onNodeWithText("Second").performClick()
        onNodeWithText("second content").assertExists()

        runOnIdle { items = listOf(second, first) }

        onNodeWithText("second content").assertExists()
        onNodeWithText("first content").assertDoesNotExist()
    }

    private fun tab(id: Long, title: String, contentId: Long) = TabItemBlockModel(
        identity = identity(id),
        title = title,
        children = listOf(FallbackLeafBlockModel(identity(contentId))),
    )

    private fun identity(id: Long) = RenderIdentity(id, id, id, id)
}
