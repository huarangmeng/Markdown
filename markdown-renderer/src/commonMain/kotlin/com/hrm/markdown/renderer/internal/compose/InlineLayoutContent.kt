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
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineRun
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun
import com.hrm.markdown.renderer.internal.layout.model.LayoutWidgetRun

@Composable
internal fun PaintInlineLayoutContent(
    block: LayoutInlineBlockModel,
    modifier: Modifier = Modifier,
) {
    val placements = remember(block.lines, block.frame) {
        buildInlineRunPlacements(block)
    }
    Layout(
        modifier = modifier,
        content = {
            for (placement in placements) {
                when (val run = placement.run) {
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
        for (index in placements.indices) {
            val placement = placements[index]
            placeables += measurables[index].measure(
                if (placement.width == 0 || placement.height == 0) {
                    Constraints.fixed(0, 0)
                } else {
                    Constraints.fixed(placement.width, placement.height)
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
            for (index in placements.indices) {
                val placement = placements[index]
                placeables[index].placeRelative(placement.x, placement.y)
            }
        }
    }
}

private data class InlineRunPlacement(
    val run: LayoutInlineRun,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

private fun buildInlineRunPlacements(block: LayoutInlineBlockModel): List<InlineRunPlacement> {
    val placements = ArrayList<InlineRunPlacement>()
    for (line in block.lines) {
        for (run in line.runs) {
            placements += InlineRunPlacement(
                run = run,
                x = (run.frame.left - block.frame.left).toInt(),
                y = (run.frame.top - block.frame.top).toInt(),
                width = run.frame.width.toInt().coerceAtLeast(0),
                height = run.frame.height.toInt().coerceAtLeast(0),
            )
        }
    }
    return placements
}
