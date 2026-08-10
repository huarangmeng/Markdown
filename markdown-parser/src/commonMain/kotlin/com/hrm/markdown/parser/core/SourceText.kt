package com.hrm.markdown.parser.core

import com.hrm.markdown.parser.LineRange

/**
 * 高性能源文本容器。
 * 对输入进行预处理：规范化行尾符、替换 NUL 字符、
 * 并构建行偏移索引以实现 O(1) 的行查找。
 *
 * Tree-sitter 风格支持：提供 [contentHash] 方法，
 * 用于计算指定行范围的内容哈希，支持增量节点复用。
 */
class SourceText private constructor(
    val content: String,
    private val lineOffsets: IntArray,
    precomputedLineHashes: LongArray? = null,
) {
    private val lineHashes: LongArray = precomputedLineHashes
        ?: computeLineHashes(content, lineOffsets)
    private val rangeHashPrefix: LongArray
    private val rangeHashPowers: LongArray

    init {
        require(lineHashes.size == lineOffsets.size) {
            "line hash count ${lineHashes.size} does not match line count ${lineOffsets.size}"
        }
        val prefix = LongArray(lineOffsets.size + 1)
        val powers = LongArray(lineOffsets.size + 1)
        powers[0] = 1L
        for (line in lineOffsets.indices) {
            prefix[line + 1] = prefix[line] * RANGE_HASH_BASE + lineHashes[line]
            powers[line + 1] = powers[line] * RANGE_HASH_BASE
        }
        rangeHashPrefix = prefix
        rangeHashPowers = powers
    }

    val length: Int get() = content.length
    val lineCount: Int get() = lineOffsets.size

    /**
     * 获取指定行的起始偏移量（基于 0 的行索引）。
     */
    fun lineStart(line: Int): Int {
        require(line in 0 until lineCount) { "Line $line out of range [0, $lineCount)" }
        return lineOffsets[line]
    }

    /**
     * 获取指定行的结束偏移量（不包含，包括换行符）。
     */
    fun lineEnd(line: Int): Int {
        require(line in 0 until lineCount) { "Line $line out of range [0, $lineCount)" }
        return if (line + 1 < lineCount) lineOffsets[line + 1] else content.length
    }

    /**
     * 获取指定行的内容（不包含末尾换行符）。
     */
    fun lineContent(line: Int): String {
        val start = lineStart(line)
        var end = lineEnd(line)
        if (end > start && content[end - 1] == '\n') end--
        return content.substring(start, end)
    }

    /**
     * 获取范围 [startLine, endLine) 内的多行内容。
     */
    fun linesContent(startLine: Int, endLine: Int): List<String> {
        val result = ArrayList<String>(endLine - startLine)
        for (i in startLine until endLine) {
            result.add(lineContent(i))
        }
        return result
    }

    /**
     * 使用二分查找确定给定偏移量所在的行。
     */
    fun lineAtOffset(offset: Int): Int {
        var lo = 0
        var hi = lineOffsets.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lineOffsets[mid] <= offset) {
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return (lo - 1).coerceAtLeast(0)
    }

    /**
     * 获取偏移量在其所在行内的列号。
     */
    fun columnAtOffset(offset: Int): Int {
        val line = lineAtOffset(offset)
        return offset - lineOffsets[line]
    }

    operator fun get(index: Int): Char = content[index]

    fun substring(startOffset: Int, endOffset: Int): String =
        content.substring(startOffset, endOffset)

    /**
     * 计算 [range] 行范围内源文本的内容哈希。
     * 每行的 FNV-1a 哈希在构造时预计算，再通过滚动前缀索引 O(1) 聚合任意连续行范围。
     */
    fun contentHash(range: LineRange): Long {
        val startLine = range.startLine.coerceIn(0, lineCount)
        val endLine = range.endLine.coerceIn(startLine, lineCount)
        val lineLength = endLine - startLine
        return rangeHashPrefix[endLine] -
            rangeHashPrefix[startLine] * rangeHashPowers[lineLength]
    }

    companion object {
        private const val FNV_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L
        private const val RANGE_HASH_BASE = -7046029254386353131L

        /**
         * 从原始输入创建 SourceText。
         * 规范化行尾符并替换 NUL 字符。
         */
        fun of(input: String): SourceText {
            val normalized = normalize(input)
            return SourceText(normalized, computeLineOffsets(normalized))
        }

        /**
         * 对当前源文本应用编辑操作，返回新的 SourceText。
         * 这是一个便捷方法，内部通过修改文本内容后重新创建 SourceText。
         */
        fun applyEdit(
            current: SourceText,
            offset: Int,
            deleteLength: Int,
            insertText: String
        ): SourceText = applyEditFast(current, offset, deleteLength, insertText)

        /**
         * 增量应用编辑：仅对插入文本做规范化，并在保留旧 lineOffsets 的基础上局部更新行偏移，
         * 避免每次编辑都对完整文本重扫一遍换行符。
         *
         * 适合 [com.hrm.markdown.parser.incremental.IncrementalEngine.applyEdit] 这类高频调用路径。
         */
        fun applyEditFast(
            current: SourceText,
            offset: Int,
            deleteLength: Int,
            insertText: String
        ): SourceText {
            val oldContent = current.content
            require(offset in 0..oldContent.length) { "offset $offset out of [0, ${oldContent.length}]" }
            require(deleteLength >= 0 && offset + deleteLength <= oldContent.length) {
                "delete range [$offset, ${offset + deleteLength}) out of [0, ${oldContent.length}]"
            }

            val normalizedInsert = if (insertText.isEmpty()) "" else normalize(insertText)
            val newContent = buildString(oldContent.length - deleteLength + normalizedInsert.length) {
                append(oldContent, 0, offset)
                append(normalizedInsert)
                append(oldContent, offset + deleteLength, oldContent.length)
            }

            val oldOffsets = current.lineOffsets
            // 保留行 0..firstAffected（这些行的起始 ≤ offset，新内容下仍然有效）。
            val firstAffectedLine = current.lineAtOffset(offset)
            val keepHead = firstAffectedLine + 1

            // 编辑区域结束所在的旧行；其后的行需要平移 delta 后保留。
            val deleteEnd = offset + deleteLength
            val lineAfterDelete = current.lineAtOffset(deleteEnd) + 1
            val tailCount = oldOffsets.size - lineAfterDelete

            // 统计新增换行符数量。
            var insertedNewlines = 0
            for (i in normalizedInsert.indices) {
                if (normalizedInsert[i] == '\n') insertedNewlines++
            }

            val newSize = keepHead + insertedNewlines + tailCount
            val newOffsets = IntArray(newSize)
            for (i in 0 until keepHead) newOffsets[i] = oldOffsets[i]
            var idx = keepHead
            for (i in normalizedInsert.indices) {
                if (normalizedInsert[i] == '\n') {
                    newOffsets[idx++] = offset + i + 1
                }
            }
            val delta = normalizedInsert.length - deleteLength
            for (i in 0 until tailCount) {
                newOffsets[idx++] = oldOffsets[lineAfterDelete + i] + delta
            }

            val newLineHashes = LongArray(newSize)
            // 受影响行之前的完整行内容没有变化。
            for (line in 0 until firstAffectedLine) {
                newLineHashes[line] = current.lineHashes[line]
            }
            // 首个受影响行以及插入换行产生的新行需要重新计算。
            val affectedEndExclusive = firstAffectedLine + insertedNewlines + 1
            for (line in firstAffectedLine until affectedEndExclusive) {
                newLineHashes[line] = hashLine(newContent, newOffsets, line)
            }
            // 删除终点之后的完整行可以直接复用旧哈希。
            for (i in 0 until tailCount) {
                newLineHashes[affectedEndExclusive + i] = current.lineHashes[lineAfterDelete + i]
            }

            return SourceText(newContent, newOffsets, newLineHashes)
        }

        internal fun normalize(input: String): String {
            // 行尾符：\r\n -> \n，\r -> \n；NUL（U+0000）-> U+FFFD。
            // 仅当输入需要规范化时才走 buildString 拷贝路径。
            var needs = false
            for (i in input.indices) {
                val c = input[i]
                if (c == '\r' || c == '\u0000') { needs = true; break }
            }
            if (!needs) return input
            return buildString(input.length) {
                var i = 0
                while (i < input.length) {
                    val c = input[i]
                    when {
                        c == '\r' -> {
                            append('\n')
                            if (i + 1 < input.length && input[i + 1] == '\n') i++
                        }
                        c == '\u0000' -> append('\uFFFD')
                        else -> append(c)
                    }
                    i++
                }
            }
        }

        private fun computeLineOffsets(normalized: String): IntArray {
            val estimatedLines = (normalized.length / 40).coerceAtLeast(16)
            var offsets = IntArray(estimatedLines)
            offsets[0] = 0
            var lineCount = 1
            for (i in normalized.indices) {
                if (normalized[i] == '\n') {
                    if (lineCount >= offsets.size) {
                        offsets = offsets.copyOf(offsets.size * 2)
                    }
                    offsets[lineCount++] = i + 1
                }
            }
            return if (lineCount == offsets.size) offsets else offsets.copyOf(lineCount)
        }

        private fun computeLineHashes(content: String, lineOffsets: IntArray): LongArray =
            LongArray(lineOffsets.size) { line -> hashLine(content, lineOffsets, line) }

        private fun hashLine(content: String, lineOffsets: IntArray, line: Int): Long {
            val start = lineOffsets[line]
            val end = if (line + 1 < lineOffsets.size) lineOffsets[line + 1] else content.length
            var hash = FNV_OFFSET_BASIS
            for (offset in start until end) {
                hash = hash xor content[offset].code.toLong()
                hash *= FNV_PRIME
            }
            return hash
        }
    }
}
