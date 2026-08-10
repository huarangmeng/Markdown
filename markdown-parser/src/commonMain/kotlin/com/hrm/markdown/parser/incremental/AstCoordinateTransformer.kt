package com.hrm.markdown.parser.incremental

import com.hrm.markdown.parser.LineRange
import com.hrm.markdown.parser.SourceRange
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Node

/** Applies one coordinate-system translation to every materialized node in a reused AST subtree. */
internal fun Node.shiftSubtreeCoordinates(linesDelta: Int, offsetDelta: Int) {
    val hasCoordinates = sourceRange != SourceRange.EMPTY || lineRange != LineRange(0, 0)
    if (hasCoordinates) {
        lineRange = lineRange.shift(linesDelta)
    }
    if (sourceRange != SourceRange.EMPTY) {
        sourceRange = sourceRange.shift(linesDelta, offsetDelta)
    }
    if (this is ContainerNode) {
        forEachMaterializedChild { child ->
            child.shiftSubtreeCoordinates(linesDelta, offsetDelta)
        }
    }
}
