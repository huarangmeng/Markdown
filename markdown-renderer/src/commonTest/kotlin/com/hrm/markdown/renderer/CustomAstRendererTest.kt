package com.hrm.markdown.renderer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.Text
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CustomAstRendererTest {
    @Test
    fun should_recompile_when_same_custom_ast_instance_is_mutated() = runComposeUiTest {
        val document = MarkdownParser().parse("before")
        val text = (document.children.single() as Paragraph).children.single() as Text
        var revision by mutableIntStateOf(0)

        setContent {
            revision // external application state drives recomposition
            Markdown(
                document = document,
                modifier = Modifier.width(320.dp),
                enableScroll = false,
                enableSelection = false,
            )
        }

        onNodeWithText("before").assertExists()
        runOnIdle {
            text.literal = "after"
            revision++
        }
        onNodeWithText("before").assertDoesNotExist()
        onNodeWithText("after").assertExists()
    }
}
