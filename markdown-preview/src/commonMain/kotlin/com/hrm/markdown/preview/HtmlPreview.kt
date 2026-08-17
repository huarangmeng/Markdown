package com.hrm.markdown.preview

import com.hrm.markdown.renderer.Markdown

internal val htmlPreviewGroups = listOf(
    PreviewGroup(
        id = "html_renderer",
        title = "HTML 渲染器",
        description = "安全行内 HTML、块级 HTML 列表与 HtmlRenderer 标准 HTML 输出",
        items = listOf(
            PreviewItem(
                id = "safe_inline_html",
                title = "Compose 安全行内 HTML",
                content = {
                    Markdown(
                        markdown = """
普通 Markdown 可以和 <strong>粗体 HTML</strong>、<em>斜体 HTML</em> 混排。

<span style="color:#ff0000; font-weight:bold">受限 style 属性</span>，以及 <code>inline code</code>。<br>这里从新的一行继续。

嵌套语义也会保留：<strong>HTML 中的 *Markdown 斜体*</strong>。
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "safe_block_html",
                title = "Compose 安全块级 HTML",
                content = {
                    Markdown(
                        markdown = """
<p align="center">Rohan 与团队</p>

<div style="text-align:center"><strong>[-NH-(CH2)6-NH-CO-(CH2)4-CO-]n</strong></div>

<section align="right"><em>容器对齐可以被内部段落继承</em></section>
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "safe_html_lists",
                title = "Compose 安全 HTML 列表",
                content = {
                    Markdown(
                        markdown = """
<ul>
<li>Hello</li>
<li><strong>World</strong></li>
</ul>

<ol start="3">
<li>Alpha</li>
<li>Beta</li>
</ol>

<ul>
<li>Parent
<ol>
<li>Nested child</li>
</ol>
</li>
</ul>
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "atomic_html_fallback",
                title = "不支持的 HTML 原子回退",
                content = {
                    Markdown(
                        markdown = """
<div style="color:red"><strong>未知块级 CSS 会让整个片段保留原文</strong></div>

行内未知属性也会保留：<span onclick="alert(1)">不会只忽略 onclick</span>。
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "html_basic",
                title = "基础 HTML 输出示例",
                content = {
                    Markdown(
                        markdown = """
以下是 `HtmlRenderer` 的使用示例：

```kotlin
val parser = MarkdownParser()
val doc = parser.parse("# Hello\n\n**Bold** text.")
val html = HtmlRenderer.render(doc)
// 输出:
// <h1 id="hello">Hello</h1>
// <p><strong>Bold</strong> text.</p>
```

也可直接调用便捷方法：

```kotlin
val html = HtmlRenderer.renderMarkdown("# Hello")
```
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "html_features",
                title = "HTML 渲染支持的元素",
                content = {
                    Markdown(
                        markdown = """
`HtmlRenderer` 完整支持所有 AST 节点：

- **块级**: 标题、段落、代码块、引用、列表、表格、数学块、Admonition 等
- **行内**: 粗体、斜体、删除线、高亮、上下标、链接、图片、脚注等
- **扩展**: 自定义容器、图表块、多列布局、分页符

配置选项：
- `softBreak`: 软换行输出（默认 `\n`，可设为 `<br />`）
- `escapeHtml`: 是否转义原始 HTML
- `xhtml`: 是否使用 XHTML 自闭合标签
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
)
