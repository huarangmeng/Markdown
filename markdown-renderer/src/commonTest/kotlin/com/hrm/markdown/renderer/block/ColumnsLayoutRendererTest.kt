package com.hrm.markdown.renderer.block

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.core.model.ColumnBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnsLayoutBlockModel
import com.hrm.markdown.renderer.internal.core.model.FallbackLeafBlockModel
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ColumnsLayoutRendererTest {
    @Test
    fun should_preserve_markdown_source_column_order_in_rtl() = runComposeUiTest {
        var firstLeft = Float.NaN
        var secondLeft = Float.NaN
        val first = FallbackLeafBlockModel(identity(11))
        val second = FallbackLeafBlockModel(identity(12))
        val model = ColumnsLayoutBlockModel(
            identity = identity(1),
            columns = listOf(
                ColumnBlockModel(identity(2), "50%", listOf(first)),
                ColumnBlockModel(identity(3), "50%", listOf(second)),
            ),
        )

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.width(300.dp)) {
                    RenderColumnsLayoutBlockModel(
                        model = model,
                        renderChildren = { children ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .onGloballyPositioned { coordinates ->
                                        if (children.single().identity.stableId == 11L) {
                                            firstLeft = coordinates.positionInRoot().x
                                        } else {
                                            secondLeft = coordinates.positionInRoot().x
                                        }
                                    }
                            )
                        },
                    )
                }
            }
        }

        waitForIdle()

        assertTrue(firstLeft < secondLeft, "RTL must not reverse Markdown source columns")
    }

    private fun identity(id: Long) = RenderIdentity(id, id, id, id)
}
