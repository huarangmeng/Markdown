package com.hrm.markdown.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.hrm.markdown.parser.ast.Document

/**
 * 渲染上下文，在组件树中通过 CompositionLocal 传递。
 * 携带文档级信息（链接引用定义、脚注定义等）和回调。
 */
@Immutable
internal data class RendererContext(
    val document: Document = Document(),
    val onLinkClick: ((String) -> Unit)? = null,
    val imageRenderer: (@Composable (url: String, altText: String, title: String?, modifier: Modifier) -> Unit)? = null,
)

internal val LocalRendererContext = staticCompositionLocalOf { RendererContext() }

@Composable
internal fun ProvideRendererContext(
    document: Document,
    onLinkClick: ((String) -> Unit)?,
    imageRenderer: (@Composable (url: String, altText: String, title: String?, modifier: Modifier) -> Unit)?,
    content: @Composable () -> Unit,
) {
    val context = RendererContext(
        document = document,
        onLinkClick = onLinkClick,
        imageRenderer = imageRenderer,
    )
    CompositionLocalProvider(LocalRendererContext provides context) {
        content()
    }
}
