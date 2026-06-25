package com.hrm.markdown.renderer

internal enum class MarkdownRenderMode {
    StaticColumn,
    LazyColumn,
}

internal fun resolveMarkdownRenderMode(
    enableSelection: Boolean,
    enableScroll: Boolean,
    isStreaming: Boolean,
): MarkdownRenderMode {
    // Selection is now an orthogonal overlay capability and no longer forces a
    // non-virtualized Column. enableSelection only decides whether the selection
    // overlay is mounted, not the render mode.
    if (isStreaming) return MarkdownRenderMode.StaticColumn
    if (enableScroll) return MarkdownRenderMode.LazyColumn
    return MarkdownRenderMode.StaticColumn
}
