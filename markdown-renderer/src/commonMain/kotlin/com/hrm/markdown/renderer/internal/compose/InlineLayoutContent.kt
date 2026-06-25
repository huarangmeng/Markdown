package com.hrm.markdown.renderer.internal.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import com.hrm.markdown.renderer.internal.layout.inline.LayoutInlineRunPlacement
import com.hrm.markdown.renderer.internal.layout.inline.runPlacements
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
import com.hrm.markdown.renderer.internal.layout.model.LayoutWidgetRun

@Composable
internal fun PaintInlineLayoutContent(
    block: LayoutInlineBlockModel,
    modifier: Modifier = Modifier,
) {
    val placements = remember(block.lines, block.frame) {
        block.runPlacements()
    }
    val paintItems = remember(placements) {
        placements.map(::InlineRunPaintItem)
    }
    Layout(
        modifier = modifier,
        content = {
            for (item in paintItems) {
                when (val run = item.placement.run) {
                    is LayoutTextRun -> key(run.identity.stableId) {
                        BasicText(
                            text = run.text,
                            modifier = Modifier.clipToBounds(),
                            style = block.style,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    is LayoutWidgetRun -> key(run.identity.stableId) {
                        val payload = block.inlinePayloads[run.id]
                        Box {
                            payload?.content?.invoke()
                        }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = ArrayList<Placeable>(measurables.size)
        for (index in paintItems.indices) {
            val item = paintItems[index]
            placeables += measurables[index].measure(
                if (item.width == 0 || item.height == 0) {
                    Constraints.fixed(0, 0)
                } else {
                    Constraints.fixed(item.width, item.height)
                }
            )
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
            for (index in paintItems.indices) {
                val item = paintItems[index]
                placeables[index].placeRelative(item.x, item.y)
            }
        }
    }
}

private data class InlineRunPaintItem(
    val placement: LayoutInlineRunPlacement,
) {
    val x: Int get() = placement.x
    val y: Int get() = placement.y
    val width: Int get() = placement.width
    val height: Int get() = placement.height
}
