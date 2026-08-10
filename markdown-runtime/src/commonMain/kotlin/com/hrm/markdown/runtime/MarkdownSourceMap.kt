package com.hrm.markdown.runtime

/**
 * 输入转换后的源码映射信息。
 *
 * 支持把转换结果中的偏移映射回原始输入；多阶段转换由 [Composite] 按逆变换顺序组合。
 */
sealed interface MarkdownSourceMap {
    /** Maps an offset in transformed output back to the corresponding original input offset. */
    fun mapOutputOffset(outputOffset: Int): Int

    data object Identity : MarkdownSourceMap {
        override fun mapOutputOffset(outputOffset: Int): Int = outputOffset
    }

    data class Segmented(
        val segments: List<Segment>
    ) : MarkdownSourceMap {
        private val indexedSegments = segments.toList()

        init {
            indexedSegments.forEachIndexed { index, segment ->
                require(segment.outputStart >= 0 && segment.inputStart >= 0) {
                    "source-map offsets must be non-negative: $segment"
                }
                require(segment.outputEnd >= segment.outputStart) {
                    "output range must be ordered: $segment"
                }
                require(segment.inputEnd >= segment.inputStart) {
                    "input range must be ordered: $segment"
                }
                if (index > 0) {
                    require(indexedSegments[index - 1].outputEnd <= segment.outputStart) {
                        "source-map segments must be sorted and non-overlapping"
                    }
                }
            }
        }

        /** Both ranges are half-open: `[start, end)`. */
        data class Segment(
            val outputStart: Int,
            val outputEnd: Int,
            val inputStart: Int,
            val inputEnd: Int,
        )

        override fun mapOutputOffset(outputOffset: Int): Int {
            require(outputOffset >= 0) { "outputOffset must be non-negative" }
            val segment = findSegment(outputOffset) ?: return outputOffset
            val outputLength = (segment.outputEnd - segment.outputStart).coerceAtLeast(0)
            val inputLength = (segment.inputEnd - segment.inputStart).coerceAtLeast(0)
            if (outputLength == 0) return segment.inputStart
            val relative = (outputOffset - segment.outputStart).coerceIn(0, outputLength)
            return segment.inputStart + (relative.toLong() * inputLength / outputLength).toInt()
        }

        private fun findSegment(outputOffset: Int): Segment? {
            var low = 0
            var high = indexedSegments.lastIndex
            while (low <= high) {
                val mid = (low + high) ushr 1
                val segment = indexedSegments[mid]
                when {
                    outputOffset < segment.outputStart -> high = mid - 1
                    outputOffset >= segment.outputEnd -> low = mid + 1
                    else -> return segment
                }
            }
            // A caret may legally sit at EOF; map it through the final segment's end boundary.
            return indexedSegments.lastOrNull()?.takeIf { outputOffset == it.outputEnd }
        }
    }

    /** Ordered from the latest output mapping back toward the original input. */
    data class Composite(
        val stages: List<MarkdownSourceMap>,
    ) : MarkdownSourceMap {
        private val indexedStages = stages.toList()

        override fun mapOutputOffset(outputOffset: Int): Int =
            indexedStages.fold(outputOffset) { offset, stage -> stage.mapOutputOffset(offset) }
    }
}

internal fun composeSourceMap(
    previous: MarkdownSourceMap,
    current: MarkdownSourceMap,
): MarkdownSourceMap {
    if (previous is MarkdownSourceMap.Identity) return current
    if (current is MarkdownSourceMap.Identity) return previous
    val currentStages = (current as? MarkdownSourceMap.Composite)?.stages ?: listOf(current)
    val previousStages = (previous as? MarkdownSourceMap.Composite)?.stages ?: listOf(previous)
    return MarkdownSourceMap.Composite(currentStages + previousStages)
}
