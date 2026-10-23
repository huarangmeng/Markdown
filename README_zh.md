<div align="center">

# 🖊️ KMP Markdown

**极速、跨平台的 Compose Multiplatform Markdown 引擎**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.huarangmeng/markdown-renderer.svg?color=orange&label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.huarangmeng.markdown)
[![CommonMark](https://img.shields.io/badge/CommonMark%200.31.2-652%2F652%20✓-brightgreen)](https://spec.commonmark.org/0.31.2/)
[![Android API](https://img.shields.io/badge/Android%20API-23%2B-34A853?logo=android&logoColor=white)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

*一套代码，像素级一致。在 Android、iOS、Desktop 和 Web 上完美渲染 Markdown。*

[English](./README.md) · [中文](./README_zh.md)

</div>

---

## ✨ 为什么选择 KMP Markdown？

`macosArm64` 库目标属于实验特性，需要配置 `org.jetbrains.compose.experimental.macos.enabled=true`。Native 渲染还需要 LaTeX、codehigh、diagram 的 macOS Native Maven 构件，现有 JVM/iOS 构件不能替代。桌面示例应用仍使用 JVM。macOS CI 检查原生测试和 Maven 变体元数据；仅添加目标不代表已完成原生链接与渲染验证。

|  | 特性 | 描述 |
|--|------|------|
| 🚀 | **增量更新** | 编辑文档或接收流式输出时复用未变化的内容 |
| 🌍 | **真正跨平台** | 一套代码，在 **Android**、**iOS**、**Desktop (JVM)**、**Web (Wasm/JS)** 上实现一致渲染；新增实验性 **macOS (Kotlin/Native)** 目标 |
| 📐 | **语法支持** | 支持 413 项语法与行为，**652/652 CommonMark 规范样例**全部通过，另支持 GFM 及 20+ 扩展 |
| 🤖 | **LLM 流式渲染** | 一等公民级别的逐 token 渲染，按显示帧合并提交 — AI 生成过程中零闪烁 |
| 🎨 | **完整主题系统** | 30+ 可配置属性，内置 GitHub 亮色/暗色主题，自动跟随系统模式 |
| 📊 | **LaTeX 数学公式** | 支持行内 `$...$` 和块级 `$$...$$` 数学公式，集成 LaTeX 渲染引擎 |
| 🔍 | **内置语法诊断** | 13+ 诊断规则，包含 WCAG 无障碍检查 — 在解析时即发现问题 |
| 🖼️ | **图片加载** | 开箱即用的 Coil3 + Ktor3 图片加载，支持尺寸指定和自定义渲染器 |
| 🖱️ | **跨块文本选择** | 跨块连续选择文字，复用系统复制/翻译菜单 |

长按并拖动可选择文本。松手或调整选择手柄后，单击 Markdown 内容区域内的空白处即可清除选区。

---

## 🎬 效果展示

### 🤖 LLM 流式渲染

实时逐 token 输出，增量解析 — 无闪烁、无重绘。

<p align="center">
  <img src="./images/llm_stream.png" width="260" alt="LLM 流式渲染效果" />
</p>

### 🔍 语法诊断与 Linting

内置语法检查，支持 WCAG 无障碍审查 — 标题跳级、脚注断链、空链接等问题一目了然。

<p align="center">
  <img src="./images/Diagnostic.png" width="260" alt="Markdown 语法诊断" />
</p>

### 🌐 丰富的 HTML 与扩展语法支持

完整的 CommonMark HTML 解析与导出、常用行内及容器 HTML 的 Compose 安全渲染，以及 GFM 表格、告示块、数学公式、代码高亮等 20+ 扩展语法。

<p align="center">
  <img src="./images/html_support.png" width="260" alt="HTML 与扩展语法支持" />
</p>

---

## 🚀 快速开始

### 安装

在 `gradle/libs.versions.toml` 中添加依赖：

```toml
[versions]
markdown = "1.5.0"

[libraries]
markdown-renderer = { module = "io.github.huarangmeng:markdown-renderer", version.ref = "markdown" }
```

在模块的 `build.gradle.kts` 中引用：

```kotlin
dependencies {
    implementation(libs.markdown.renderer)
}
```

> 💡 这一项依赖已包含语法支持、插件接口、Coil3 + Ktor3 图片加载，以及 Mermaid / PlantUML / DOT 图表渲染。

### 基本用法

```kotlin
import com.hrm.markdown.renderer.Markdown

@Composable
fun MyScreen() {
    Markdown(
        markdown = """
            # Hello World
            
            这是一个包含 **粗体** 和 *斜体* 的段落。
            
            - 列表项 1
            - 列表项 2
            
            ```kotlin
            fun hello() = println("Hello")
            ```
        """.trimIndent(),
        modifier = Modifier.fillMaxSize(),
    )
}
```

就这么简单 — **3 行代码**即可在所有平台上渲染精美的 Markdown。

### LaTeX 配置

直接传入原生 `LatexConfig`。行内和块级公式共享字体、主题、回调与测量配置。

```kotlin
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

Markdown(
    markdown = text,
    latexConfig = LatexConfig(
        fontSize = 24.sp,
        theme = LatexTheme.material3(),
        accessibilityEnabled = true,
        onHyperlinkClick = { url -> /* host navigation */ },
    ),
)
```

默认使用 `LatexConfig()`（20.sp、跟随系统亮暗、透明背景）；Markdown 配色不会覆盖公式配置。

**不建议移除配套的 KaTeX 字体**：系统字体可能出现缺字或排版偏差。
如果确实不需要携带字体资源，可排除 `latex-fonts-katex` 模块；默认配置会安全回退到系统字体，不会因字体缺失而崩溃：

```kotlin
implementation(libs.markdown.renderer) {
    exclude(group = "io.github.huarangmeng", module = "latex-fonts-katex")
}
```

---

## 🔌 插件扩展

通过 `directivePlugins` 接入视频等自定义语法，并使用原生 Compose 组件渲染。
插件也可以处理标准的 `{% tag ... %}` 指令语法及 HTML 导出。

基础用法：

```kotlin
Markdown(
    markdown = """
这里是自定义语法：

!VIDEO[Demo](https://cdn.example.com/a.mp4){poster=https://cdn.example.com/a.jpg}
    """.trimIndent(),
    directivePlugins = listOf(VideoDirectivePlugin),
)
```

插件骨架：

```kotlin
object VideoDirectivePlugin : MarkdownDirectivePlugin {
    override val id: String = "video"

    override val inputTransformers = listOf(VideoSyntaxTransformer())

    override val blockDirectiveRenderers = mapOf(
        "video" to { scope ->
            VideoPlayer(
                url = scope.directive.args.getValue("url"),
                poster = scope.directive.args["poster"],
                title = scope.directive.args["title"],
            )
        }
    )
}

class VideoSyntaxTransformer : MarkdownInputTransformer {
    override val id: String = "video-syntax"

    override fun transform(input: String): MarkdownTransformResult {
        val normalized = input.replace(
            Regex("""!VIDEO\[(.*?)\]\((.*?)\)\{poster=(.*?)\}""")
        ) { match ->
            val title = match.groupValues[1]
            val url = match.groupValues[2]
            val poster = match.groupValues[3]
            """{% video title="$title" url="$url" poster="$poster" %}"""
        }
        return MarkdownTransformResult(markdown = normalized)
    }
}
```

输入转换器默认通过 `MarkdownTransformerStreamingSupport.RestartOnRewrite` 支持流式输入。
转换改写先前的文本时，组件会自动刷新内容。只有在追加输入必定保留此前全部转换结果时，才应声明
`AppendSafe`；声明 `Unsupported` 可要求每次按完整内容更新。通过 `LocalMarkdownSourceMap`，
可以将转换后的文本位置映射回原始输入。

插件与转换器作为配置使用：行为不变时复用实例，修改行为时提供新实例。
替换插件的渲染回调会更新显示方式，不中断流式输出；替换输入转换器会刷新内容及源码映射。

在 `DirectiveBlockRenderScope` 和 `DirectiveInlineRenderScope` 中，
通过 `scope.directive` 读取指令名称、参数和内容信息。
HTML 导出使用的 `HtmlDirectiveFallback` 与 `HtmlInlineDirectiveFallback` 接收对应的指令数据。

HTML 导出会走同一条 directive 运行时链路：

```kotlin
val html = MarkdownHtml.render(
    markdown = markdown,
    directivePlugins = listOf(VideoDirectivePlugin),
)
```

---

## 🤖 LLM 流式集成

专为 AI/LLM 场景打造。启用 `isStreaming` 即可实现无闪烁的增量渲染：

```kotlin
import com.hrm.markdown.parser.MarkdownParserConfig

var text by remember { mutableStateOf("") }
var isStreaming by remember { mutableStateOf(true) }

LaunchedEffect(Unit) {
    llmTokenFlow.collect { token ->
        text += token
    }
    isStreaming = false
}

Markdown(
    markdown = text,
    isStreaming = isStreaming,
    parserConfig = MarkdownParserConfig.LlmStreaming,
)
```

`LlmStreaming` 以 16 字符为阈值合并短更新，遇到换行提前显示。
生成结束或取消后，将 `isStreaming` 设为 `false`，即可显示全部剩余文本。
代码正文随输出更新，未闭合的代码围栏也会显示当前内容。

- 生成过程中可以重试、清空或替换全文，显示内容跟随最新输入。
- 流式输出期间，未闭合的围栏、数学块和强调标记会获得临时显示补全。
- 同一显示帧内的多次 token 更新合并为最新文档更新。

无论是流式生成还是编辑文档，都将最新的完整文本传给 `markdown`。
组件会自动处理增量更新及必要的内容刷新。

选择文字期间更新正文时，选区端点和正在拖动的手柄都会校正到有效的 UTF-16 字符边界。
删除选中块会同时清除选区与拖动状态，复制不会使用已经失效的端点。

---

## 交互与标准组件插槽

通过 `MarkdownInteractions` 统一配置链接、引用（`[@key]`）和图片回调。未配置链接回调时使用 Compose 的 `LocalUriHandler` 打开链接。

```kotlin
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownBlockSlot
import com.hrm.markdown.renderer.MarkdownComponents
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.renderer.MarkdownInteractions
import com.hrm.markdown.renderer.MarkdownSoftBreakPolicy

Markdown(
    markdown = text,
    interactions = MarkdownInteractions(
        onLinkClick = { url -> /* navigate */ },
        onCitationClick = { event -> /* event.key, event.anchorBoundsInWindow */ },
        onImageClick = { event ->
            // Read event.gallery only when opening a document-order image viewer.
            val gallery = event.gallery
            // gallery.images[gallery.initialIndex] is the clicked occurrence.
        },
    ),
    softBreakPolicy = MarkdownSoftBreakPolicy.LineBreak,
    components = MarkdownComponents(
        codeBlockHeader = MarkdownBlockSlot(48.dp) { code ->
            TextButton(onClick = { runCode(code.code, code.language) }) { Text("Run") }
        },
        tableToolbar = MarkdownBlockSlot(48.dp) { table ->
            Text("${table.rowCount} rows / ${table.columnCount} columns")
        },
    ),
)
```

`runCode` 由宿主应用实现。图片默认使用 Coil 加载，可通过 `components.image` 自定义；`event.gallery` 提供文档图片图集。

插槽内容应保持在声明高度内，工具栏文字不计入 Markdown 复制内容。`MarkdownTableInfo.rowCount` 包含表头行。

外层容器负责垂直滚动时，设置 `enableScroll = false`。

软换行默认为 `Space`；使用 `LineBreak` 可在显示与复制时保留换行。HTML 导出同样支持 `softBreakPolicy`。

---

## 🎨 颜色、排版、尺寸与状态

所有设置均可省略，`Markdown(markdown = text)` 默认跟随系统配色。Markdown 自身样式分为三个独立入口；代码、公式、图表使用各自库的原生配置。

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.hrm.codehigh.renderer.CodeDefaults
import com.hrm.codehigh.renderer.CodeTypography
import com.hrm.codehigh.renderer.CodeLayout

Markdown(
    markdown = text,
    colors = MarkdownDefaults.colors(link = Color(0xFF0969DA)),
    typography = MarkdownTypography(
        h1 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        text = TextStyle(fontSize = 16.sp, lineHeight = 28.sp),
    ),
    layout = MarkdownLayout(blockSpacing = 12.dp),
    codeColors = CodeDefaults.oneDarkProColors(),
    codeTypography = CodeTypography(
        text = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
        inlineCode = TextStyle(fontSize = 15.sp),
    ),
    codeLayout = CodeLayout(contentPadding = PaddingValues(16.dp)),
    interactions = MarkdownInteractions(
        onLinkClick = { url -> /* 宿主导航 */ },
        onCodeTokenClick = { token -> /* codehigh CodeToken */ },
    ),
)
```

配色可选 `MarkdownDefaults.lightColors()`、`darkColors()` 或 `material3Colors()`。通过 `ProvideMarkdownDefaults` 提供应用默认设置，实例参数优先。

代码外观使用 codehigh 的 `CodeColors`、`CodeTypography`、`CodeLayout`，同一份配置可用于 Markdown 和 `CodeBlock`。

| 设置内容 | 入口 |
|---|---|
| 方言、Emoji、Lint、流式合并阈值 | `parserConfig = MarkdownParserConfig(...)` |
| 软换行、标题编号 | `softBreakPolicy`、`enableHeadingNumbering` |
| 内部滚动、文本选择 | `enableScroll`、`enableSelection` |
| 流式生成状态 | `isStreaming` |
| 链接、引用、图片与代码 Token 点击 | `interactions = MarkdownInteractions(...)` |
| 头尾插槽、图片渲染、代码/表格工具栏、告示图标 | `components = MarkdownComponents(...)` |
| 代码外观（codehigh 原生类型） | `codeColors`、`codeTypography`、`codeLayout` |
| 公式与图表配置 | `latexConfig`、`diagramTheme` |
| 指令插件 | `directivePlugins` |

`onCodeTokenClick` 接收 codehigh 的 `CodeToken`，其中 `range` 相对于当前代码块。

```kotlin
val state = rememberMarkdownState()
val scope = rememberCoroutineScope()
Button(onClick = { scope.launch { state.scrollToBlock(10) } }) { Text("跳转") }
Markdown(markdown = text, state = state, components = MarkdownComponents(
    header = { Text("前言") },
    footer = { Text("结束") },
))
```

使用 `rememberMarkdownState()` 控制滚动和清除选区；块索引不包含头尾插槽。

---

## 📐 全面的语法支持（413 项功能）

<details>
<summary><b>📦 块级元素</b> — 结构化内容所需的一切</summary>

| 功能 | 详情 |
|------|------|
| **标题** | ATX 标题 (`# ~ ######`)、Setext 标题 (`===`/`---`)、自定义 ID (`{#id}`)、自动生成锚点 |
| **段落** | 多行合并、空行分隔、延续行 |
| **代码块** | 围栏代码块 (`` ``` ``/`~~~`) 支持语言高亮（20+ 语言）、缩进代码块、行号、行高亮 |
| **块引用** | 嵌套引用、延续行、内部块级元素 |
| **列表** | 无序/有序/任务列表、嵌套、紧凑/松散区分；任务复选框与正文间距为 8.dp |
| **表格 (GFM)** | 列对齐、单元格内行内格式、管道符转义 |
| **分隔线** | `---`、`***`、`___` |
| **HTML 块** | 所有 7 种 CommonMark 类型；常用容器、标题、引用、代码块、列表及文字对齐的 Compose 安全片段渲染 |
| **链接引用定义** | 完整支持各种标题格式 |

</details>

<details>
<summary><b>✏️ 行内元素</b> — 丰富的文本格式信手拈来</summary>

| 功能 | 详情 |
|------|------|
| **强调** | 粗体、斜体、粗斜体、嵌套、CJK 分隔符规则 |
| **删除线** | `~~text~~`、`~text~` |
| **行内代码** | 单/多反引号、空格剥离 |
| **链接** | 行内链接、引用链接（完整/折叠/简写）、自动链接、GFM 裸 URL、属性块 |
| **图片** | 行内图片、引用图片、`=WxH` 尺寸指定、属性块、自动 Figure 转换 |
| **行内 HTML** | CommonMark 标签、注释、CDATA、处理指令；常用格式、链接、换行和图片的 Compose 安全语义渲染 |
| **转义与实体** | 32 个可转义字符、命名/数字 HTML 实体 |
| **换行** | 硬换行（空格/反斜杠）、软换行 |

</details>

<details>
<summary><b>🔌 扩展语法</b> — 超越标准 Markdown 的强大功能</summary>

| 功能 | 语法 |
|------|------|
| **数学公式 (LaTeX)** | `$...$` 行内、`$$...$$` 块级、`\tag{}`、`\ref{}` |
| **脚注** | `[^label]` 引用、多行定义、块级内容 |
| **告示/提醒块** | `> [!NOTE]`、`> [!TIP]`、`> [!IMPORTANT]`、`> [!WARNING]`、`> [!CAUTION]` |
| **高亮** | `==text==` |
| **上标/下标** | `^text^`、`~text~`、`<sup>`、`<sub>` |
| **插入文本** | `++text++` |
| **Emoji** | `:smile:` 指令 (200+)、ASCII 表情 (40+)、自定义映射 |
| **定义列表** | 术语 + `: definition` 格式 |
| **Front Matter** | YAML (`---`) 和 TOML (`+++`) |
| **目录 (TOC)** | `[TOC]` 支持深度、排除、排序选项 |
| **自定义容器** | `:::type` 支持嵌套、CSS 类、ID |
| **图表块** | Mermaid、PlantUML、Graphviz 等 |
| **多列布局** | `:::columns` 支持百分比/像素宽度 |
| **选项卡块** | `=== "Tab Title"` MkDocs Material 风格 |
| **指令** | `{% tag args %}...{% endtag %}` 支持位置/关键字参数 |
| **折叠文本** | `>!hidden text!<` Discord/Reddit 风格 |
| **Wiki 链接** | `[[page]]`、`[[page\|显示文本]]` |
| **注音 (Ruby)** | `{漢字\|かんじ}` 注音标注 |
| **参考文献** | `[@key]` 引用与 `[^bibliography]` 定义 |
| **块属性** | `{.class #id key=value}` kramdown/Pandoc 风格 |
| **分页符** | `***pagebreak***` 用于打印/PDF 导出 |
| **样式文本** | `[text]{.red style="color:red"}` 行内 CSS |

</details>

---

## 🔍 内置语法诊断

**13+ 条诊断规则：**

| 规则 | 严重级别 | 描述 |
|------|----------|------|
| 标题层级跳跃 | ⚠️ 警告 | h1 → h3，缺少 h2 |
| 重复标题 ID | ⚠️ 警告 | 多个标题生成了相同的锚点 |
| 无效脚注引用 | ❌ 错误 | 引用了未定义的脚注 |
| 未使用的脚注 | ⚠️ 警告 | 脚注已定义但从未被引用 |
| 空链接目标 | ⚠️ 警告 | `[text]()` 缺少 URL |
| 缺少替代文本 | ⚠️ 警告 | 图片没有描述文字 |
| 空链接文本 | ⚠️ 警告 | 屏幕阅读器无法识别的链接 |
| 非描述性链接 | ⚠️ 警告 | "点击这里"、"了解更多" 等链接 |
| 缺少代码语言 | ℹ️ 信息 | 围栏代码块未指定语言标识 |
| 表格缺少表头 | ⚠️ 警告 | 屏幕阅读器需要 `<th>` |
| 替代文本过长 | ⚠️ 警告 | Alt 文本超过 125 个字符 |

> 这些规则检查部分与无障碍相关的源码问题，不代表完整的 WCAG 合规认证。

### 辅助技术阅读语义

Compose 渲染器默认提供基础阅读语义，无需启用 `enableLinting`：

- ATX、Setext 和受支持的安全 HTML 标题提供标题导航标记。
- 换行与样式产生的行内绘制片段合并为段落或列表项阅读单元，并保持源码顺序；链接和交互组件保留原生操作。
- 列表提供集合与条目位置；任务项将正文与勾选状态关联，作为只读复选框呈现，不提供修改操作。

上述语义由公共 Compose 渲染路径提供；TalkBack、VoiceOver 等平台读屏体验仍需在目标设备上验证。预览的“渲染与质量 → 无障碍阅读”提供混合内容示例。

---

## 🧪 规范兼容性

| 规范 | 状态 |
|------|------|
| [CommonMark 0.31.2](https://spec.commonmark.org/0.31.2/) | **652/652 (100%)** ✅ |
| [GFM 0.29](https://github.github.com/gfm/) | 表格、任务列表、删除线、自动链接 ✅ |
| [Markdown Extra](https://michelf.ca/projects/php-markdown/extra/) | 脚注、定义列表、缩写、围栏代码 ✅ |

### 稳定性验证

| 范围 | 已验证的行为 |
|------|--------------|
| 增量与流式解析 | 四种方言下，连续编辑及流式前缀的结果与独立完整解析一致 |
| 异步生命周期 | 覆盖快速替换全文、取消、切换配置及退出页面的回归场景 |
| 极端输入 | 512 层引用、百万字符单行、密集分隔符和巨大未闭合块通过独立进程检查 |
| 回归检查 | JVM 测试及 Parser 性能预算通过；Parser、Renderer 测试可编译至 JS 和 Wasm |

当前版本的执行结果见 [CI](https://github.com/huarangmeng/Markdown/actions/workflows/test.yml)。

### Flavour 系统

通过 `parserConfig` 选择语法配置：

| Flavour | 支持的语法 |
|---|---|
| `CommonMarkFlavour` | 严格 CommonMark |
| `GFMFlavour` | CommonMark、表格、任务列表和删除线 |
| `MarkdownExtraFlavour` | 脚注、定义列表、缩写和表格 |
| `ExtendedFlavour` | 全部已支持扩展，默认使用此配置 |

组件和 HTML 导出可以共用同一份配置：

```kotlin
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.parser.flavour.GFMFlavour
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownHtml

val config = MarkdownParserConfig(flavour = GFMFlavour)
Markdown(markdown = input, parserConfig = config)
val html = MarkdownHtml.render(markdown = input, parserConfig = config)
```

配置不可变，会保留传入的 Emoji 别名。使用 `copy` 修改配置项，或通过 `with`
追加语法、处理器，其余语法设置自动保留。例如，以下配置限制标题层级，同时适用于显示和导出：

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
Markdown(markdown = input, parserConfig = config)
```

`BlockPostProcessor` 处理独立块：修改后返回原块、返回替换块，或返回 `null` 删除该块。
依赖全文的转换使用 `PostProcessor`。内容变化时，组件会自动应用这些转换。
配置与扩展行为不变时，复用已有实例即可。

### Compose 安全 HTML

`Markdown()` 无需 WebView，即可跨平台渲染一组安全的 HTML 子集：

```markdown
普通文本与 <strong>粗体</strong>、<em>斜体</em>、
<span style="color:red">样式文本</span>、<a href="https://example.com">链接</a> 混排。<br>
下一行还可以包含 <img src="https://example.com/icon.png" alt="图标">。
```

当前支持 `strong`/`b`、`em`/`i`、`del`/`s`/`strike`、`mark`、`sup`、`sub`、
`ins`、`u`、`code`、`kbd`、`span`、`a`、`br` 和 `img`。行内 HTML 注释不显示；
格式标签及 `br` 不接受属性，`span` 只接受能够完整映射的 `style`/`class`，`a` 只接受
`href`，`img` 只接受 `src`、`alt`、`title`、`width` 和 `height`。`span` 的 CSS 仅支持
文字/背景颜色、字重、字形、下划线和删除线。未知属性、未知 CSS、危险 URL、错配或
未闭合标签都会保留源码，不会忽略一部分后继续渲染，也不会静默丢失内容。

块级安全片段支持 `p`、`div`、`center`、`article`、`section`、`main`、`header`、
`footer`，`h1`-`h6`、`blockquote`、`hr`、安全的 `pre`/`code` 代码块，以及安全的
`ul`/`ol`/`li` 列表结构。容器内可以使用上述安全行内标签，并通过 `align` 或受限的
`style="text-align:..."` 设置 `left`、`start`、`center`、`right` 或 `end`；`ol`
接受整数 `start` 属性，`ul` 和 `li` 不接受属性。`pre` 可直接包含文本，或包含单个
`code` 子元素；`code` 仅接受 `class="language-..."` 或 `class="lang-..."` 声明语言。
其中 `left`/`right` 是物理方向，`start`/`end` 跟随当前布局方向。Compose 会按
CommonMark HTML 块类型明确分流：类型 2 注释可隐藏，类型 6/7 的安全片段可映射，
类型 1 中安全的 `pre` 代码片段可映射，其他类型 1 以及类型 3/4/5 保持原文。格式错误、
未知属性，或表格、表单、脚本、任意 CSS 等未支持结构，都会原子地回退为可见的原始
HTML，不进行半截渲染。

按照 CommonMark 规则，块级标签必须独占行首。`文本 <p>块</p> 文本` 不是合法的块级
写法，会保留为行内源码；应将 `p` 独立成行。通过 `MarkdownHtml.render`
导出 HTML 时，原始 HTML 仍会继续透传。

---

## 🖼️ 自定义图片渲染

内置 Coil3 自动处理图片加载。需要自定义渲染？很简单：

```kotlin
Markdown(
    markdown = markdownText,
    components = MarkdownComponents(image = { data, modifier ->
        // data.url, data.altText, data.width, data.height 均可获取
        AsyncImage(
            model = data.url,
            contentDescription = data.altText,
            modifier = modifier,
        )
    }),
)
```

支持在 Markdown 中指定图片尺寸：
```markdown
![照片](https://example.com/photo.png =400x300)
![自动宽度](https://example.com/photo.png =x200)
```

---

## ▶️ 运行 Demo

```bash
# Android
./gradlew :composeApp:assembleDebug

# Desktop (JVM)
./gradlew :composeApp:run

# Web (Wasm)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web (JS)
./gradlew :composeApp:jsBrowserDevelopmentRun

# iOS — 在 Xcode 中打开 iosApp/ 目录
```

---

## 📊 功能支持总览

| # | 类别 | 已支持 / 清单项 |
|---|------|--------|
| 1 | 标题 | 17/17 (100%) |
| 2 | 段落 | 5/5 (100%) |
| 3 | 代码块 | 17/17 (100%) |
| 4 | 块引用 | 8/8 (100%) |
| 5 | 列表 | 20/20 (100%) |
| 6 | 分隔线 | 6/6 (100%) |
| 7 | 表格 (GFM) | 11/11 (100%) |
| 8 | HTML 块 | 12/12 (100%) |
| 9 | 链接引用定义 | 12/12 (100%) |
| 10 | 块级扩展 | 85/85 (100%) |
| 11 | 强调 | 13/13 (100%) |
| 12 | 删除线 | 4/4 (100%) |
| 13 | 行内代码 | 8/8 (100%) |
| 14 | 链接 | 27/27 (100%) |
| 15 | 图片 | 17/17 (100%) |
| 16 | 行内 HTML | 10/10 (100%) |
| 17 | 转义与实体 | 10/10 (100%) |
| 18 | 换行 | 5/5 (100%) |
| 19 | 行内扩展 | 50/50 (100%) |
| 20 | 流式引擎 | 27/27 (100%) |
| 21 | 字符与编码 | 10/10 (100%) |
| 22 | HTML 生成器 | 12/12 (100%) |
| 23 | 诊断 / WCAG | 19/19 (100%) |
| 24 | 指令 | 8/8 (100%) |
| | **总计** | **413/413 (100%)** |

> 📖 完整详情：[PARSER_COVERAGE_ANALYSIS.md](./markdown-parser/PARSER_COVERAGE_ANALYSIS.md)

---

## 🔗 渲染依赖说明

`markdown-renderer` 当前通过以下外部库提供数学公式、代码高亮和图表渲染能力：

| 能力 | 本项目使用的 Maven 模块 | 仓库地址 |
|------|-------------------------|----------|
| LaTeX 数学公式 | `io.github.huarangmeng:latex-base`、`io.github.huarangmeng:latex-parser`、`io.github.huarangmeng:latex-renderer`、`io.github.huarangmeng:latex-fonts-katex` | [huarangmeng/latex](https://github.com/huarangmeng/latex) |
| 代码高亮 | `io.github.huarangmeng:codehighlight-parser`、`io.github.huarangmeng:codehighlight-render` | [huarangmeng/codehigh](https://github.com/huarangmeng/codehigh) |
| 图表块 | `io.github.huarangmeng:diagram-core`、`io.github.huarangmeng:diagram-layout`、`io.github.huarangmeng:diagram-parser`、`io.github.huarangmeng:diagram-render` | [huarangmeng/diagram](https://github.com/huarangmeng/diagram) |

如果你只需要其中一部分能力，也可以直接按需依赖对应的上游库。

---

## 📄 开源协议

```
MIT License · Copyright (c) 2026 huarangmeng
```

本项目采用 MIT 开源协议 — 详见 [LICENSE](LICENSE) 文件。
