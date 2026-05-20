package com.hrm.markdown.parser.block.postprocessors

import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Node

/**
 * 后处理器注册表。
 *
 * 管理所有已注册的 [PostProcessor]，按优先级顺序执行。
 *
 * ## 使用方式
 * ```kotlin
 * // 使用内置默认处理器
 * val registry = PostProcessorRegistry.withDefaults()
 *
 * // 添加自定义处理器
 * registry.register(MyCustomPostProcessor())
 *
 * // 空注册表（不执行任何后处理）
 * val empty = PostProcessorRegistry()
 * ```
 */
class PostProcessorRegistry {
    // 使用 Copy-on-Write 模式：每次修改都创建新列表，保证遍历安全
    // processors 列表本身是不可变的（val），但引用可以指向新的不可变列表
    private var _processors: List<PostProcessor> = emptyList()
    // 缓存排序后的列表，避免每次 processAll 都排序
    private var _sortedProcessors: List<PostProcessor>? = null

    fun register(processor: PostProcessor) {
        _processors = _processors + processor
        _sortedProcessors = null
    }

    fun registerAll(vararg processors: PostProcessor) {
        _processors = _processors + processors.toList()
        _sortedProcessors = null
    }

    /**
     * 按优先级顺序执行所有后处理器。
     *
     * 使用 Copy-on-Write 模式保证线程安全：
     * - `_processors` 和 `_sortedProcessors` 的读写是原子引用操作
     * - 遍历的是不可变列表快照，不会被并发修改
     */
    fun processAll(document: Document) {
        val snapshot = _sortedProcessors ?: _processors.sortedBy { it.priority }.also { _sortedProcessors = it }
        for (processor in snapshot) {
            processor.process(document)
        }
    }

    companion object {
        /**
         * 创建预注册了所有内置后处理器的注册表。
         *
         * 内置处理器按优先级排列：
         * 1. [HeadingIdProcessor] (100) — 自动生成标题 ID (slug)
         * 2. [HtmlFilterProcessor] (200) — GFM 禁止的 HTML 标签过滤
         * 3. [AbbreviationProcessor] (300) — 缩写替换
         * 4. [DiagramProcessor] (400) — 围栏代码块 → 图表块转换
         */
        fun withDefaults(): PostProcessorRegistry {
            return PostProcessorRegistry().apply {
                register(HeadingIdProcessor())
                register(HtmlFilterProcessor())
                register(AbbreviationProcessor())
                register(DiagramProcessor())
            }
        }

        /**
         * 从节点中提取纯文本的便捷方法。
         * 委托给 [HeadingIdProcessor.extractPlainText]。
         */
        fun extractPlainText(node: Node): String {
            return HeadingIdProcessor.extractPlainText(node)
        }
    }
}
