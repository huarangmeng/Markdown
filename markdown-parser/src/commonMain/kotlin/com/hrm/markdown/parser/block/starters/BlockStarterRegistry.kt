package com.hrm.markdown.parser.block.starters

import com.hrm.markdown.parser.block.OpenBlock
import com.hrm.markdown.parser.core.LineCursor

/**
 * 块级开启器注册表。
 *
 * 管理所有已注册的 [BlockStarter]，按优先级排序。
 * 提供统一的块开启检测入口，按优先级顺序依次尝试各开启器。
 *
 * ## 使用方式
 * ```kotlin
 * val registry = BlockStarterRegistry()
 * registry.register(HeadingStarter())
 * registry.register(FencedCodeBlockStarter(source))
 * // ...
 * val newBlock = registry.tryStart(cursor, lineIdx, tip)
 * ```
 *
 * ## 扩展新语法
 * ```kotlin
 * class MyCustomBlockStarter : BlockStarter {
 *     override val priority = 350
 *     override val canInterruptParagraph = true
 *     override fun tryStart(...) = ...
 * }
 * registry.register(MyCustomBlockStarter())
 * ```
 */
class BlockStarterRegistry {
    private val starters = mutableListOf<BlockStarter>()
    private var sorted = false

    /**
     * 注册一个块级开启器。
     */
    fun register(starter: BlockStarter) {
        starters.add(starter)
        sorted = false
    }

    /**
     * 注册多个块级开启器。
     */
    fun registerAll(vararg starters: BlockStarter) {
        this.starters.addAll(starters)
        sorted = false
    }

    /**
     * 按优先级顺序尝试所有已注册的开启器。
     *
     * @return 第一个成功开启的 [OpenBlock]，或 null
     */
    fun tryStart(cursor: LineCursor, lineIdx: Int, tip: OpenBlock): OpenBlock? {
        ensureSorted()
        for (starter in starters) {
            val snap = cursor.snapshot()
            val result = starter.tryStart(cursor, lineIdx, tip)
            if (result != null) return result
            cursor.restore(snap)
        }
        return null
    }

    /**
     * 检查指定的块开启器是否能中断段落。
     */
    fun canInterruptParagraph(block: OpenBlock): Boolean {
        val tag = block.starterTag
        if (tag != null) {
            val starter = starters.find { it::class.simpleName == tag }
            if (starter is HtmlBlockStarter) {
                return starter.canInterruptParagraphForType(block.htmlType)
            }
            if (starter != null) return starter.canInterruptParagraph
        }
        return true
    }

    /**
     * 获取所有已注册的开启器（按优先级排序）。
     */
    fun allStarters(): List<BlockStarter> {
        ensureSorted()
        return starters.toList()
    }

    private fun ensureSorted() {
        if (!sorted) {
            starters.sortBy { it.priority }
            sorted = true
        }
    }
}
