<div align="center">

# 🖊️ KMP Markdown

**极速、跨平台的 Compose Multiplatform Markdown 引擎**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.0-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.huarangmeng/markdown-parser.svg?color=orange&label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.huarangmeng.markdown)
[![CommonMark](https://img.shields.io/badge/CommonMark%200.31.2-652%2F652%20✓-brightgreen)](https://spec.commonmark.org/0.31.2/)
[![Android API](https://img.shields.io/badge/Android%20API-23%2B-34A853?logo=android&logoColor=white)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

*一套代码，像素级一致。在 Android、iOS、Desktop 和 Web 上完美渲染 Markdown。*

[English](./README.md) · [中文](./README_zh.md)

</div>

---

## ✨ 为什么选择 KMP Markdown？

|  | 特性 | 描述 |
|--|------|------|
| 🚀 | **极速解析** | 基于 AST 的递归下降解析器，支持增量解析 — 仅重新解析变更部分 |
| 🌍 | **真正跨平台** | 一套代码，在 **Android**、**iOS**、**Desktop (JVM)**、**Web (Wasm/JS)** 上实现一致渲染 |
| 📐 | **100% 覆盖** | 372 项 Markdown 功能，**652/652 CommonMark 规范测试**全部通过，另支持 GFM 及 20+ 扩展 |
| 🤖 | **LLM 流式渲染** | 一等公民级别的逐 token 渲染，5fps 节流刷新 — AI 生成过程中零闪烁 |
| 🎨 | **完整主题系统** | 30+ 可配置属性，内置 GitHub 亮色/暗色主题，自动跟随系统模式 |
| 📊 | **LaTeX 数学公式** | 支持行内 `$...$` 和块级 `$$...$$` 数学公式，集成 LaTeX 渲染引擎 |
| 🔍 | **内置语法诊断** | 13+ 诊断规则，包含 WCAG 无障碍检查 — 在解析时即发现问题 |
| 🖼️ | **图片加载** | 开箱即用的 Coil3 + Ktor3 图片加载，支持尺寸指定和自定义渲染器 |
| 🖱️ | **跨 Block 选中** | 基于 LazyColumn 的自研选区层 —— 跨 block 连续选中，复用系统复制/翻译菜单，超长文档首帧不卡 |

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

完整的 HTML 块级/行内支持，GFM 表格、告示块、数学公式、代码高亮等 20+ 扩展语法。

<p align="center">
  <img src="./images/html_support.png" width="260" alt="HTML 与扩展语法支持" />
</p>

---

## 🚀 快速开始

### 安装

在 `gradle/libs.versions.toml` 中添加依赖：

```toml
[versions]
markdown = "1.2.1"

[libraries]
markdown-parser = { module = "io.github.huarangmeng:markdown-parser", version.ref = "markdown" }
markdown-runtime = { module = "io.github.huarangmeng:markdown-runtime", version.ref = "markdown" }
markdown-renderer = { module = "io.github.huarangmeng:markdown-renderer", version.ref = "markdown" }
```

在模块的 `build.gradle.kts` 中引用：

```kotlin
dependencies {
    implementation(libs.markdown.parser)
    implementation(libs.markdown.runtime)
    implementation(libs.markdown.renderer)
}
```

> 💡 `markdown-renderer` 会自动传递引入 Coil3 + Ktor3 图片加载能力，以及 Mermaid / PlantUML / DOT 图表所需的 `diagram-render`。

### 基本用法

```kotlin
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.codehigh.theme.OneDarkProTheme

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
        theme = MarkdownTheme.auto(), // 自动跟随系统日夜间模式
        codeTheme = OneDarkProTheme, // 可选：直接传入 codehigh 主题
    )
}
```

就这么简单 — **3 行代码**即可在所有平台上渲染精美的 Markdown。

---

## 🔌 插件扩展

本项目提供“官方 directive 扩展网关 + runtime 输入转换器 + renderer 插件分发”的终极扩展方案：

- `markdown-parser` 保持纯净：只解析 Markdown + 官方 directive AST
- 外部特殊语法（例如 `!VIDEO[...]`）通过 transformer 转换为 `{% video ... %}`
- renderer 通过插件注册表把 `video` directive 渲染为原生 Compose 内容

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

`DirectiveBlockRenderScope` 和 `DirectiveInlineRenderScope` 现在基于 snapshot。
读取结构化 directive 数据时，请统一使用 `scope.directive` 作为唯一入口。
HTML 导出侧的 `HtmlDirectiveFallback` 与 `HtmlInlineDirectiveFallback`
也改为接收 snapshot，而不再直接暴露 parser AST 节点。

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
    isStreaming = isStreaming, // 启用增量解析 + 5fps 节流渲染
)
```

**底层机制：**
- ✅ 仅重新解析"脏"尾部区域 — 稳定的块直接复用
- ✅ 流式输出期间自动闭合未闭合的围栏、数学块、强调标记
- ✅ 延迟行内解析 — 先处理块结构，按需解析行内元素
- ✅ FNV-1a 内容哈希，O(1) 时间复杂度的块稳定性检测

---

## 🎨 主题定制

```kotlin
// 内置主题
Markdown(markdown = text, theme = MarkdownTheme.light())  // GitHub 亮色
Markdown(markdown = text, theme = MarkdownTheme.dark())   // GitHub 暗色
Markdown(markdown = text, theme = MarkdownTheme.auto())   // 自动检测

// 完全自定义（30+ 可配置属性）
Markdown(
    markdown = text,
    theme = MarkdownTheme(
        headingStyles = listOf(
            TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
            // h2 ~ h6 ...
        ),
        bodyStyle = TextStyle(fontSize = 16.sp),
        codeBlockBackground = Color(0xFFF5F5F5),
        // ...更多属性
    ),
    onLinkClick = { url -> /* 处理链接点击 */ },
)
```

代码高亮与代码块/行内代码主题由 `codehigh` 提供，`markdown-renderer` 不再内置独立的语法高亮器。

```kotlin
import com.hrm.codehigh.theme.DraculaProTheme
import com.hrm.codehigh.theme.LocalCodeTheme
import com.hrm.codehigh.theme.OneDarkProTheme

// 方式 1：对单个 Markdown 直接传入 codehigh 主题
Markdown(
    markdown = text,
    theme = MarkdownTheme.auto(),
    codeTheme = OneDarkProTheme,
)

// 方式 2：通过 codehigh 的 CompositionLocal 统一注入默认代码主题
CompositionLocalProvider(
    LocalCodeTheme provides DraculaProTheme
) {
    Markdown(
        markdown = text,
        theme = MarkdownTheme.auto(),
    )
}
```

- `theme` 负责普通 Markdown 内容样式，如标题、正文、表格、引用、数学公式等
- `codeTheme` 只负责代码块与行内代码的高亮和配色
- 如果不传 `codeTheme`，则使用 `codehigh` 当前的默认主题

---

## 📐 全面的语法支持（372 项功能）

<details>
<summary><b>📦 块级元素</b> — 结构化内容所需的一切</summary>

| 功能 | 详情 |
|------|------|
| **标题** | ATX 标题 (`# ~ ######`)、Setext 标题 (`===`/`---`)、自定义 ID (`{#id}`)、自动生成锚点 |
| **段落** | 多行合并、空行分隔、延续行 |
| **代码块** | 围栏代码块 (`` ``` ``/`~~~`) 支持语言高亮（20+ 语言）、缩进代码块、行号、行高亮 |
| **块引用** | 嵌套引用、延续行、内部块级元素 |
| **列表** | 无序/有序/任务列表、嵌套、紧凑/松散区分 |
| **表格 (GFM)** | 列对齐、单元格内行内格式、管道符转义 |
| **分隔线** | `---`、`***`、`___` |
| **HTML 块** | 所有 7 种 CommonMark 类型 |
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
| **行内 HTML** | 标签、注释、CDATA、处理指令 |
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

一次启用，全局检查：

```kotlin
val parser = MarkdownParser(enableLinting = true)
val document = parser.parse(markdown)

document.diagnostics.forEach { diagnostic ->
    println("第 ${diagnostic.line} 行: [${diagnostic.severity}] ${diagnostic.message}")
}
```

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

> 遵循 [WCAG 2.1 AA](https://www.w3.org/TR/WCAG21/) 无障碍标准。

---

## 🏗️ 项目架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Your Compose App                        │
├─────────────────────────────────────────────────────────────┤
│  markdown-preview                                           │
│  交互式展示与演示                                           │
├─────────────────────────────────────────────────────────────┤
│  markdown-renderer                                          │
│  AST → Compose UI / HTML                                    │
│  块级/行内渲染、主题、图片加载                              │
├─────────────────────────────────────────────────────────────┤
│  markdown-runtime                                           │
│  Directive 插件注册、输入转换、运行时管线                   │
├─────────────────────────────────────────────────────────────┤
│  markdown-parser                                            │
│  Markdown → AST                                             │
│  流式 / 增量 / Flavour 系统                                 │
│  诊断 / Linting / 后处理器                                  │
└─────────────────────────────────────────────────────────────┘
```

| 模块 | 描述 |
|------|------|
| `:markdown-parser` | 核心解析引擎 — Markdown 字符串 → AST。支持流式、增量、多 Flavour。 |
| `:markdown-runtime` | Directive 运行时层 — 插件注册表、输入转换器、directive 管线、source map。 |
| `:markdown-renderer` | 渲染引擎 — AST → Compose UI。主题、图片加载、代码高亮。 |
| `:markdown-preview` | 交互式展示 — 分类浏览所有支持功能的演示。 |
| `:composeApp` | 跨平台 Demo 应用（Android/iOS/Desktop/Web）。 |
| `:androidApp` | Android 独立 Demo 应用。 |

---

## 🧪 规范兼容性

| 规范 | 状态 |
|------|------|
| [CommonMark 0.31.2](https://spec.commonmark.org/0.31.2/) | **652/652 (100%)** ✅ |
| [GFM 0.29](https://github.github.com/gfm/) | 表格、任务列表、删除线、自动链接 ✅ |
| [Markdown Extra](https://michelf.ca/projects/php-markdown/extra/) | 脚注、定义列表、缩写、围栏代码 ✅ |

### Flavour 系统

按需配置启用哪些语法特性：

```kotlin
// 严格 CommonMark — 不启用扩展
val doc = MarkdownParser(CommonMarkFlavour).parse(input)

// GFM — CommonMark + 表格、删除线、自动链接
val doc = MarkdownParser(GFMFlavour).parse(input)

// 扩展模式（默认）— 启用全部功能
val doc = MarkdownParser().parse(input)

// 一键 HTML 渲染
val html = HtmlRenderer.renderMarkdown(input, flavour = CommonMarkFlavour)
```

---

## 🖼️ 自定义图片渲染

内置 Coil3 自动处理图片加载。需要自定义渲染？很简单：

```kotlin
Markdown(
    markdown = markdownText,
    imageContent = { data, modifier ->
        // data.url, data.altText, data.width, data.height 均可获取
        AsyncImage(
            model = data.url,
            contentDescription = data.altText,
            modifier = modifier,
        )
    },
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

### 运行测试

```bash
./gradlew :markdown-parser:jvmTest      # Parser 模块测试
./gradlew :markdown-renderer:jvmTest     # Renderer 模块测试
./gradlew jvmTest                        # 全部测试
```

---

## 📊 功能覆盖总览

| # | 类别 | 覆盖率 |
|---|------|--------|
| 1 | 标题 | 17/17 (100%) |
| 2 | 段落 | 5/5 (100%) |
| 3 | 代码块 | 17/17 (100%) |
| 4 | 块引用 | 8/8 (100%) |
| 5 | 列表 | 20/20 (100%) |
| 6 | 分隔线 | 6/6 (100%) |
| 7 | 表格 (GFM) | 11/11 (100%) |
| 8 | HTML 块 | 10/10 (100%) |
| 9 | 链接引用定义 | 12/12 (100%) |
| 10 | 块级扩展 | 85/85 (100%) |
| 11 | 强调 | 13/13 (100%) |
| 12 | 删除线 | 4/4 (100%) |
| 13 | 行内代码 | 8/8 (100%) |
| 14 | 链接 | 27/27 (100%) |
| 15 | 图片 | 17/17 (100%) |
| 16 | 行内 HTML | 8/8 (100%) |
| 17 | 转义与实体 | 10/10 (100%) |
| 18 | 换行 | 5/5 (100%) |
| 19 | 行内扩展 | 50/50 (100%) |
| 20 | 流式引擎 | 27/27 (100%) |
| 21 | 字符与编码 | 10/10 (100%) |
| 22 | HTML 生成器 | 12/12 (100%) |
| 23 | 诊断 / WCAG | 19/19 (100%) |
| 24 | 指令 | 8/8 (100%) |
| | **总计** | **372/372 (100%)** |

> 📖 完整详情：[PARSER_COVERAGE_ANALYSIS.md](./markdown-parser/PARSER_COVERAGE_ANALYSIS.md)

---

## 🔗 渲染依赖说明

`markdown-renderer` 当前通过以下外部库提供数学公式、代码高亮和图表渲染能力：

| 能力 | 本项目使用的 Maven 模块 | 仓库地址 |
|------|-------------------------|----------|
| LaTeX 数学公式 | `io.github.huarangmeng:latex-base`、`io.github.huarangmeng:latex-parser`、`io.github.huarangmeng:latex-renderer` | [huarangmeng/latex](https://github.com/huarangmeng/latex) |
| 代码高亮 | `io.github.huarangmeng:codehighlight-parser`、`io.github.huarangmeng:codehighlight-render` | [huarangmeng/codehigh](https://github.com/huarangmeng/codehigh) |
| 图表块 | `io.github.huarangmeng:diagram-core`、`io.github.huarangmeng:diagram-layout`、`io.github.huarangmeng:diagram-parser`、`io.github.huarangmeng:diagram-render` | [huarangmeng/diagram](https://github.com/huarangmeng/diagram) |

如果你只需要其中一部分能力，也可以直接按需依赖对应的上游库。

---

## 📄 开源协议

```
MIT License · Copyright (c) 2026 huarangmeng
```

本项目采用 MIT 开源协议 — 详见 [LICENSE](LICENSE) 文件。
