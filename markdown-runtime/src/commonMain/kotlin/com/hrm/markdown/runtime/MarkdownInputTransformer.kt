package com.hrm.markdown.runtime

/**
 * Markdown 输入转换器。
 *
 * 用于把外部自定义语法转换成官方 directive 语法，避免污染 parser。
 */
interface MarkdownInputTransformer {
    val id: String

    /**
     * Declares whether transforming a longer append-only input always preserves the complete
     * transformed prefix produced for the shorter input. Only such transformers can participate
     * in incremental streaming without forcing parser session restarts.
     */
    val streamingSupport: MarkdownTransformerStreamingSupport
        get() = MarkdownTransformerStreamingSupport.Unsupported

    fun transform(input: String): MarkdownTransformResult
}

enum class MarkdownTransformerStreamingSupport {
    Unsupported,
    AppendSafe,
}
