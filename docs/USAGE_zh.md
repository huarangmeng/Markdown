# 使用指南

[README](../README_zh.md) · [English](USAGE.md)

示例面向当前源码，使用新 API 前请留意 README 中的版本说明。
除类型声明外，UI 片段均放在 Composable 中，使用 `import com.hrm.markdown.renderer.*`
及常规 Compose 导入；`text` 代表应用的文档内容。

- [点击与源码属性](#点击与源码属性)
- [自定义选区菜单](#自定义选区菜单)
- [文档查找](#文档查找)
- [行内装饰](#行内装饰)
- [块插槽与图片](#块插槽与图片)
- [代码与公式样式](#代码与公式样式)
- [Directive 插件](#directive-插件)
- [解析转换](#解析转换)
- [HTML 行为](#html-行为)

## 点击与源码属性

```kotlin
Markdown(
    markdown = text,
    interactions = MarkdownInteractions(
        onLinkClick = { url -> /* Navigate to url. */ },
        onCitationClick = { event -> /* Open the source identified by event.key. */ },
        onImageClick = { event ->
            val gallery = event.gallery
            // gallery.images[gallery.initialIndex] is the clicked occurrence.
        },
        onCodeTokenClick = { token -> /* Inspect token.text, token.type, token.range. */ },
        onInlineElementClick = { event ->
            // event.kind, text, payload, attributes, anchorBoundsInWindow
        },
    ),
)
```

未提供 `onLinkClick` 时，链接由 Compose 的 `LocalUriHandler` 打开。图片图集仅在读取时构建，
按文档顺序排列并区分重复 URL。提供 `onImageClick` 时优先走图片专属回调；否则图片可通过
`onInlineElementClick` 分发，`kind = InlineElementKinds.Image`，并可从 `image` 事件访问图集。

行内事件描述实际渲染的文字片段，嵌套格式可能将同一语义范围拆成多个片段。
`payload` 携带库已解释的语义，如引用 key；`attributes` 携带支持透传的源码属性，供宿主按约定读取，
例如 `<mark data-id="note-1">text</mark>`。单纯粗体、斜体和删除线不会单独产生语义点击事件。
`anchorBoundsInWindow` 使用像素单位，也可能为空；代码 Token 的范围相对于所在代码块。

## 自定义选区菜单

通过 `components.selectionToolbar` 替换平台工具栏：

```kotlin
Markdown(
    markdown = text,
    components = MarkdownComponents(selectionToolbar = { info, actions ->
        SelectionMenu(
            info = info,
            onCopy = actions::copySelection,
            onSelectAll = actions::selectAll,
            onDismiss = actions::dismiss,
        )
    }),
)
```

`SelectionMenu` 是应用自行实现的 Composable，可依据 `info.boundsInWindow` 定位。
`info.text` 是选中的纯文本；`info.ranges` 提供逐块正文、`blockStableId`、半开字符区间及
`blockAttributes`。受支持源码块携带的属性可用于关联宿主内容。这不会放开任意 HTML 块属性，
仍需遵循 [HTML 属性限制](#html-行为)。

`copySelection()` 复制并清除选区，`selectAll()` 扩展为全选，`dismiss()` 清除选区并关闭菜单。
不提供该插槽时继续使用平台工具栏。`rememberMarkdownState()` 也可控制滚动和清除选区；
`state.scrollToBlock(index)` 使用顶层块下标，不计头尾插槽。

## 文档查找

`Markdown(markdown = ...)` 和 `Markdown(document = ...)` 均接受 `textSearchState`。
查询框和结果切换按钮由应用提供；查找不会重新解析文档、修改用户选区或打开选区菜单。

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

val search = rememberMarkdownTextSearchState()
val scope = rememberCoroutineScope()
Column(Modifier.fillMaxSize()) {
    TextField(value = search.query, onValueChange = { search.setQuery(it) })
    Button(
        enabled = search.matches.isNotEmpty(),
        onClick = {
            val results = search.matches
            if (results.isNotEmpty()) {
                val next = (results.indexOf(search.activeMatch) + 1) % results.size
                scope.launch { search.reveal(results[next]) }
            }
        },
    ) { Text("Next (${search.matches.size})") }
    Markdown(
        markdown = text,
        modifier = Modifier.weight(1f),
        textSearchState = search,
    )
}
```

父布局需要提供有限高度。`reveal(match)` 将匹配的首个实际文本行滚动到可见区域，并返回是否找到
可用的渲染几何；再次调用也会重新定位同一结果。内部滚动会将完全屏外的结果居中，可见结果保持
不动，部分可见的结果移到最近边缘；文档边界可能限制居中效果。

外层负责滚动时，设置 `enableScroll = false`。如需复用查找居中策略，在**创建滚动容器之前**提供
以下策略。需导入相应 Compose foundation/runtime API（包括 `LocalBringIntoViewSpec`），
并按所用版本要求启用 `ExperimentalFoundationApi`：

```kotlin
val spec = rememberMarkdownTextSearchBringIntoViewSpec(
    search, obscuredTopPx = toolbarHeightPx, obscuredBottomPx = bottomBarHeightPx,
)
CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
    Column(Modifier.verticalScroll(scrollState)) {
        Markdown(markdown = text, enableScroll = false, textSearchState = search)
    }
}
```

遮挡量由宿主以实际像素传入。普通焦点/选区滚动继续交给 `fallback` 策略，选区手势会取消待完成的
查找滚动。

| 配置 | 行为 |
|---|---|
| `setQuery(query)` | 字面匹配，结果不重叠且按文档顺序排列，默认忽略大小写 |
| `setQuery(query, ignoreCase = false)` | 区分大小写，仍会去除首尾空白并折叠连续空白 |
| `highlightColor` / `highlightActiveMatch` | 当前结果背景，默认黄色、30% 不透明度 |
| `highlightAllMatches` / `allMatchHighlightColor` | 可选的其他结果背景，默认关闭，颜色为黄色、15% 不透明度 |
| `clear()` | 清除查询、结果、高亮，并取消待完成的定位 |
| `textSearchState = null` | 关闭面板时解除查找绑定，释放其索引与几何回调 |

当前结果颜色会覆盖其他结果颜色。关闭 `highlightActiveMatch` 后仍可保留全部结果高亮。
两种高亮均可在 `enableSelection = false` 时使用。

查找覆盖标题、段落、嵌套正文和表格单元格，可跨粗体/链接等行内样式边界，不跨块、单元格或行内
组件。代码块、行内代码、图片替代文本、公式/图表源码及其他组件替代内容不参与查找。
隐藏标签页中的正文可能有逻辑匹配，但显示后才能定位。

结果提供 `excerpt`、`blockStableId`、`documentRevision` 和 `startInBlock` / `endInBlock`：
偏移是渲染器逻辑块正文中的 UTF-16 半开区间，**不是 Markdown 源码位置**。空白折叠不会改变这些
偏移。大小写匹配遵循 Kotlin 的简单比较，不进行语言区域折叠或 Unicode 归一化。

每个 Markdown 实例使用独立状态，并在 UI 线程操作。查询、文档修订或绑定变化后，请丢弃旧匹配结果。
新的定位请求会取消待完成的请求；调用方协程的取消仍向上传播。绑定前设置的查询会在绑定时执行。

## 行内装饰

`inlineDecoration` 接收语义文字片段及其 `payload`、`attributes`，返回 `MarkdownInlineUnderline`
可绘制下划线和可选末端标记，返回 `null` 则不添加自定义装饰。下例用色块演示占位，可替换为资源
或矢量 `Painter` 绘制图标：

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp

val painter = remember { ColorPainter(Color.Blue) }
Markdown(
    markdown = "==plain **bold** text==",
    interactions = MarkdownInteractions(inlineDecoration = { request ->
        if (request.kind != InlineElementKinds.Highlight) null else MarkdownInlineUnderline(
            color = Color.Blue,
            dashWidth = 4.dp,
            gapWidth = 2.dp,
            endMarker = MarkdownInlineEndMarker(
                color = Color.Blue,
                diameter = 8.dp,
                painter = painter,
            ),
        )
    }),
)
```

- `dashWidth` + `gapWidth` 设置虚线，`dotDiameter` + `dotGap` 设置点线，均不提供时为实线；点线优先。
- Painter 标记真实占据 `gap + diameter` 的行内宽度，空间不足时换行；按 `color` 着色并裁剪到边长为 `diameter` 的方框。
- 每个语义范围只有一个文字末端，跨粗体/斜体或换行不会重复标记；相邻独立范围保留各自标记。
- 标记不增加源码、复制、选择或搜索字符。装饰状态变化会更新布局；`endMarker = null` 仅移除标记，保留下划线。
- 省略 `painter` 时保留原有纯绘制圆点，默认直径 4.dp、间距 3.dp，不预留行内宽度。

回调还会在排版前执行以确定 Painter 标记，应保持无副作用，不要假定每个范围只调用一次。
标记依附文字而非组件，锚点跟随末字形的双向文本方向；这不代表完整 RTL 段落布局支持，
复杂混合方向内容需在宿主验证。

## 块插槽与图片

```kotlin
Markdown(
    markdown = text,
    components = MarkdownComponents(
        header = { Text("Introduction") },
        footer = { Text("End") },
        codeBlockHeader = MarkdownBlockSlot(48.dp) { code ->
            TextButton(onClick = { runCode(code.code, code.language) }) { Text("Run") }
        },
        tableToolbar = MarkdownBlockSlot(48.dp) { table ->
            Text("${table.rowCount} rows / ${table.columnCount} columns")
        },
        image = { data, modifier ->
            AsyncImage(
                model = data.url,
                contentDescription = data.altText,
                modifier = modifier,
            )
        },
    ),
)
```

`runCode` 由应用实现，`AsyncImage` 来自 Coil3。代码/表格插槽内容应放在声明的高度内，工具栏文字
不进入 Markdown 复制；表格行数包含表头。图片数据提供 `url`、`altText`、`width`、`height`，
自定义图片绘制与图片点击回调分别配置。

图片可指定尺寸：`![photo](https://example.com/photo.png =400x300)`，或
`![auto width](https://example.com/photo.png =x200)`。
通过 `softBreakPolicy = MarkdownSoftBreakPolicy.LineBreak` 可在显示和复制时保留普通换行，
默认策略为 `Space`；HTML 导出接受同样的配置。

## 代码与公式样式

代码设置复用 codehigh 原生类型，可同时用于 Markdown 和独立 `CodeBlock`。
行内公式与块公式共用 LaTeX 原生配置：

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.codehigh.renderer.CodeDefaults
import com.hrm.codehigh.renderer.CodeLayout
import com.hrm.codehigh.renderer.CodeTypography
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

Markdown(
    markdown = text,
    codeColors = CodeDefaults.oneDarkProColors(),
    codeTypography = CodeTypography(
        text = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
        inlineCode = TextStyle(fontSize = 15.sp),
    ),
    codeLayout = CodeLayout(contentPadding = PaddingValues(16.dp)),
    latexConfig = LatexConfig(
        fontSize = 24.sp,
        theme = LatexTheme.material3(),
        accessibilityEnabled = true,
    ),
)
```

默认使用 `LatexConfig()`（20.sp、系统明暗、透明背景），Markdown 配色不会覆盖它。
`onHyperlinkClick` 等 LaTeX 回调也在此配置。建议保留内置 KaTeX 字体，以获得一致的字形和测量结果。
如需移除，可排除字体模块；组件会回退到系统字体，但可能缺字或产生排版差异：

```kotlin
implementation(libs.markdown.renderer) {
    exclude(group = "io.github.huarangmeng", module = "latex-fonts-katex")
}
```

## Directive 插件

通过 `directivePlugins` 注册自定义 Compose 内容：

```kotlin
import androidx.compose.material3.Text
import com.hrm.markdown.runtime.MarkdownBlockDirectiveRenderer
import com.hrm.markdown.runtime.MarkdownDirectivePlugin

object NoticePlugin : MarkdownDirectivePlugin {
    override val id = "notice"
    override val blockDirectiveRenderers: Map<String, MarkdownBlockDirectiveRenderer> = mapOf(
        "notice" to { scope -> Text(scope.directive.args["text"].orEmpty()) },
    )
}

// Inside a composable:
Markdown(
    markdown = """{% notice text="Hello" %}""",
    directivePlugins = listOf(NoticePlugin),
)
```

对于 `!VIDEO[...]` 等外部语法，可增加 `MarkdownInputTransformer` 转换为标准 `{% tag ... %}`
指令，参考[视频插件示例](../markdown-preview/src/commonMain/kotlin/com/hrm/markdown/preview/DirectivePluginPreview.kt)。
转换器默认使用 `MarkdownTransformerStreamingSupport.RestartOnRewrite`；只有追加输入始终保留
此前完整转换结果时，才能声明 `AppendSafe`；使用 `Unsupported` 则按全文更新。
`LocalMarkdownSourceMap` 将转换后位置映射回原始输入。

插件和转换器行为不变时应复用实例；替换转换器会刷新内容与源码映射。渲染回调可更新而无需重置
解析状态。`Markdown(document = ...)` 重载要求输入转换已在构造文档前完成。

`MarkdownHtml.render(markdown = text, directivePlugins = plugins)` 使用相同的指令管线。
可通过 `HtmlDirectiveFallback` / `HtmlInlineDirectiveFallback` 提供插件的 HTML 输出；
渲染 scope 与 HTML fallback 均可读取对应的指令数据。

## 解析转换

解析配置不可变，修改选项使用 `copy`，追加语法或处理器使用方言的 `with`，其余设置保留。
配置及扩展行为不变时复用实例。例如，同时限制 Compose 与 HTML 的标题层级：

```kotlin
import androidx.compose.runtime.remember
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.block.postprocessors.BlockPostProcessor
import com.hrm.markdown.parser.flavour.CommonMarkFlavour

val config = remember {
    MarkdownParserConfig(
        flavour = CommonMarkFlavour.with(BlockPostProcessor { block ->
            if (block is Heading) block.level = minOf(block.level, 3)
            block
        }),
    )
}
Markdown(markdown = text, parserConfig = config)
val html = MarkdownHtml.render(markdown = text, parserConfig = config)
```

`BlockPostProcessor` 返回修改后的块、替换块，或返回 `null` 删除该块；
依赖全文的转换使用 `PostProcessor`。

## HTML 行为

Compose 渲染受限的 HTML 子集，不支持的片段回退为可见源码。
作为不透明数据传给回调的属性不会被解释为布局、CSS 指令或可执行行为。

| 元素组 | 支持的行为 |
|---|---|
| `strong`/`b`、`em`/`i`、`del`/`s`/`strike`、`mark`、`sup`、`sub`、`ins`、`u`、`code`、`kbd` | 原生文字格式，非 `on*` 属性保留为不透明数据 |
| `span` | 完整可映射的 `style`/`class`，其他非 `on*` 属性保留为不透明数据 |
| `a`、`img`、`br` | `a` 仅接受安全 `href`；`img` 接受安全 `src`、`alt`、`title`、`width`、`height`；`br` 不接受属性 |
| `p`、`div`、`center`、`article`、`section`、`main`、`header`、`footer` | 容器支持 `align` 或受限的 `style="text-align:..."` |
| `h1`–`h6`、`blockquote`、`hr` | 不带属性的原生块 |
| `pre` / `code`、`ul` / `ol` / `li` | 安全的代码/列表结构，代码语言通过 `language-...` / `lang-...` 类名声明；`ol start` 接受整数 |

span CSS 支持文字/背景颜色、字重、字形、下划线与删除线。未知 span 类名、不支持的 CSS、
`on*` 事件属性、URL 属性中的危险地址及格式错误的结构都会回退为源码；采用严格白名单的元素
也会拒绝未知属性。不支持的片段（包括表格、表单和脚本）整体保留为原始 HTML，避免部分渲染。

块标签需遵循 CommonMark 的块位置规则，例如 `<p>...</p>` 应独立成行，不要放在段落文字中间。
行内注释与受支持的块注释隐藏，不支持的块级 HTML 保留源码。
对齐支持 `left`、`start`、`center`、`right`、`end`，
其中 `start`/`end` 跟随布局方向。

这些规则作用于 Compose 显示；`MarkdownHtml.render` 会透传原始 HTML，宿主应用需自行决定
如何处理导出的 HTML。
