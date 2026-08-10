package com.hrm.markdown.runtime

import com.hrm.markdown.parser.log.HLog

private const val TAG = "MarkdownDirectiveRegistry"

class MarkdownDirectiveRegistry(
    directivePlugins: List<MarkdownDirectivePlugin>,
) {
    val directivePlugins: List<MarkdownDirectivePlugin> = directivePlugins
        .sortedWith(compareBy<MarkdownDirectivePlugin> { it.priority }.thenBy { it.id })
        .toList()

    private val blockRenderers = LinkedHashMap<String, MarkdownBlockDirectiveRenderer>()
    private val inlineRenderers = LinkedHashMap<String, MarkdownInlineDirectiveRenderer>()
    private val htmlFallbacks = LinkedHashMap<String, HtmlDirectiveFallback>()
    private val htmlInlineFallbacks = LinkedHashMap<String, HtmlInlineDirectiveFallback>()
    private val transformers = mutableListOf<MarkdownInputTransformer>()

    /** Aggregate streaming contract for the complete transformer pipeline. */
    val streamingSupport: MarkdownTransformerStreamingSupport
        get() = when {
            transformers.any {
                it.streamingSupport == MarkdownTransformerStreamingSupport.Unsupported
            } -> MarkdownTransformerStreamingSupport.Unsupported
            transformers.all {
                it.streamingSupport == MarkdownTransformerStreamingSupport.AppendSafe
            } -> MarkdownTransformerStreamingSupport.AppendSafe
            else -> MarkdownTransformerStreamingSupport.RestartOnRewrite
        }

    /** Whether transformed revisions may use the stateful streaming parser. */
    val supportsStreaming: Boolean
        get() = streamingSupport != MarkdownTransformerStreamingSupport.Unsupported

    /** Whether every transformer proves that parser session restarts are unnecessary. */
    val supportsStreamingFastPath: Boolean
        get() = streamingSupport == MarkdownTransformerStreamingSupport.AppendSafe

    val hasTransformers: Boolean
        get() = transformers.isNotEmpty()

    init {
        for (plugin in this.directivePlugins) {
            transformers += plugin.inputTransformers
            for ((tagName, renderer) in plugin.blockDirectiveRenderers) {
                if (blockRenderers.containsKey(tagName)) {
                    HLog.w(TAG) { "block directive renderer overridden: tag=$tagName, plugin=${plugin.id}" }
                }
                blockRenderers[tagName] = renderer
            }
            for ((tagName, renderer) in plugin.inlineDirectiveRenderers) {
                if (inlineRenderers.containsKey(tagName)) {
                    HLog.w(TAG) { "inline directive renderer overridden: tag=$tagName, plugin=${plugin.id}" }
                }
                inlineRenderers[tagName] = renderer
            }
            for ((tagName, fallback) in plugin.htmlDirectiveFallbacks) {
                if (htmlFallbacks.containsKey(tagName)) {
                    HLog.w(TAG) { "html directive fallback overridden: tag=$tagName, plugin=${plugin.id}" }
                }
                htmlFallbacks[tagName] = fallback
            }
            for ((tagName, fallback) in plugin.htmlInlineDirectiveFallbacks) {
                if (htmlInlineFallbacks.containsKey(tagName)) {
                    HLog.w(TAG) { "html inline directive fallback overridden: tag=$tagName, plugin=${plugin.id}" }
                }
                htmlInlineFallbacks[tagName] = fallback
            }
        }
    }

    fun inputTransformers(): List<MarkdownInputTransformer> = transformers.toList()

    fun findBlockDirectiveRenderer(tagName: String): MarkdownBlockDirectiveRenderer? = blockRenderers[tagName]

    fun findInlineDirectiveRenderer(tagName: String): MarkdownInlineDirectiveRenderer? = inlineRenderers[tagName]

    fun findHtmlDirectiveFallback(tagName: String): HtmlDirectiveFallback? = htmlFallbacks[tagName]

    fun findHtmlInlineDirectiveFallback(tagName: String): HtmlInlineDirectiveFallback? = htmlInlineFallbacks[tagName]

    companion object {
        val Empty = MarkdownDirectiveRegistry(emptyList())
    }
}
