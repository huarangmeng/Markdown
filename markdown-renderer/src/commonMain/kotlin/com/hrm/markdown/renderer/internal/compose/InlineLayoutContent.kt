package com.hrm.markdown.renderer.internal.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
import com.hrm.markdown.renderer.internal.layout.model.LayoutWidgetRun

@Composable
internal fun PaintInlineLayoutContent(
    block: LayoutInlineBlockModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Layout(
        modifier = modifier,
        content = {
            for (line in block.lines) {
                for (run in line.runs) {
                    when (run) {
                        is LayoutTextRun -> key(run.identity.stableId) {
                            BasicText(
                                text = run.text,
                                modifier = Modifier
                                    .size(
                                        width = with(density) { run.frame.width.toDp() },
                                        height = with(density) { run.frame.height.toDp() },
                                    )
                                    .clipToBounds(),
                                style = block.style,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        is LayoutWidgetRun -> key(run.identity.stableId) {
                            val payload = block.inlinePayloads[run.id]
                            Box(
                                modifier = Modifier.size(
                                    width = with(density) { run.frame.width.toDp() },
                                    height = with(density) { run.frame.height.toDp() },
                                )
                            ) {
                                payload?.content?.invoke()
                            }
                        }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = ArrayList<Placeable>(measurables.size)
        var measurableIndex = 0
        for (line in block.lines) {
            for (run in line.runs) {
                val measurable = measurables[measurableIndex++]
                val width = run.frame.width.toInt().coerceAtLeast(0)
                val height = run.frame.height.toInt().coerceAtLeast(0)
                placeables += measurable.measure(
                    if (width == 0 || height == 0) {
                        Constraints.fixed(0, 0)
                    } else {
                        Constraints.fixed(width, height)
                    }
                )
            }
        }

        val desiredWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            block.frame.width.toInt().coerceAtLeast(constraints.minWidth)
        }
        val desiredHeight = block.contentFrame.height.toInt()
            .coerceAtLeast(constraints.minHeight)
            .let { height ->
                if (constraints.hasBoundedHeight) height.coerceAtMost(constraints.maxHeight) else height
            }

        layout(desiredWidth, desiredHeight) {
            var placeableIndex = 0
            for (line in block.lines) {
                for (run in line.runs) {
                    val placeable = placeables[placeableIndex++]
                    val x = (run.frame.left - block.frame.left).toInt()
                    val y = (run.frame.top - block.frame.top).toInt()
                    placeable.placeRelative(x, y)
                }
            }
        }
    }
}
