package com.hrm.markdown.renderer.internal.layout.inline

import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineRun

/**
 * A flattened inline run with block-local geometry.
 *
 * The renderer, selection index, and selection hit-testing all depend on the
 * same coordinate convention: x/y are relative to [LayoutInlineBlockModel.frame].
 */
internal data class LayoutInlineRunPlacement(
    val lineIndex: Int,
    val runIndex: Int,
    val run: LayoutInlineRun,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal fun LayoutInlineBlockModel.runPlacements(): List<LayoutInlineRunPlacement> {
    val placements = ArrayList<LayoutInlineRunPlacement>()
    lines.forEachIndexed { lineIndex, line ->
        line.runs.forEachIndexed { runIndex, run ->
            placements += LayoutInlineRunPlacement(
                lineIndex = lineIndex,
                runIndex = runIndex,
                run = run,
                x = (run.frame.left - frame.left).toInt(),
                y = (run.frame.top - frame.top).toInt(),
                width = run.frame.width.toInt().coerceAtLeast(0),
                height = run.frame.height.toInt().coerceAtLeast(0),
            )
        }
    }
    return placements
}
