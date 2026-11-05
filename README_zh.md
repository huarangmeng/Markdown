<div align="center">

# KMP Markdown

**面向 Compose Multiplatform 的 Markdown 渲染组件，支持增量更新与 LLM 流式输出。**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.huarangmeng/markdown-renderer.svg?color=orange&label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.huarangmeng.markdown)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Android · iOS · Desktop（JVM）· Web（Wasm/JS）· 实验性 macOS Native

[English](README.md) · [中文](README_zh.md) · [使用指南](docs/USAGE_zh.md)

</div>

## 主要能力

| 能力 | 包含内容 |
|---|---|
| Markdown 与扩展 | CommonMark、GFM 表格与任务列表、脚注、提示框、目录、标签页与自定义容器 |
| 富内容 | LaTeX 公式、代码高亮、Mermaid / PlantUML / DOT 图表，以及 Coil3 图片加载 |
| 流式输出与编辑 | 增量更新、未闭合语法的临时补全、流式更新合并 |
| 阅读交互 | 跨块选择、自定义选区菜单、链接/图片/引用点击，以及语义文本装饰 |
| 文档查找 | 正文字面匹配、滚动定位，以及独立的当前/全部结果高亮 |
| 定制与扩展 | 颜色、排版、布局、组件插槽与 Directive 插件 |
| 导出与诊断 | 共用解析配置的 HTML 导出，以及可选的源码诊断 |

<p align="center">
  <img src="images/llm_stream.png" width="220" alt="LLM 流式输出" />
  <img src="images/Diagnostic.png" width="220" alt="源码诊断" />
  <img src="images/html_support.png" width="220" alt="HTML 与扩展语法" />
</p>

## 快速开始

在版本目录和共享模块中添加渲染器：

```toml
# gradle/libs.versions.toml
[versions]
markdown = "1.6.2"

[libraries]
markdown-renderer = { module = "io.github.huarangmeng:markdown-renderer", version.ref = "markdown" }
```

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.markdown.renderer)
        }
    }
}
```

渲染器已包含解析器、插件 API、图片加载、公式、代码高亮和图表能力。
应用无需依赖预览或 Demo 模块。

本文描述当前源码。文档查找、自定义选区菜单和行内装饰在 `release-1.6.2` 标签之后加入，
使用这些能力需要包含相应提交的版本，或从源码构建。

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.hrm.markdown.renderer.Markdown

@Composable
fun Article() {
    Markdown(
        markdown = "# Hello\n\nMarkdown with **Compose Multiplatform**.",
        modifier = Modifier.fillMaxSize(),
    )
}
```

组件默认启用内部滚动，需要父布局提供有限高度；由外层容器滚动时，设置 `enableScroll = false`。
文本选择默认开启；完成选择后，点击内容区域内的空白处即可清除选区。

### 平台说明

Android 最低支持 API 23，桌面 Demo 使用 JVM。实验性 `macosArm64` 库目标需要开启
`org.jetbrains.compose.experimental.macos.enabled=true`，并依赖 LaTeX（含 `latex-fonts-katex`）、
codehigh 和 diagram 的 macOS Native 发布物，不能用 JVM 或 iOS 产物替代。
声明目标不代表已完成原生渲染验证，请查看所用版本的 Native 测试结果。

## 流式输出

每次更新传入截至当前的累计全文，并在生成完成或取消时设置 `isStreaming = false`：

```kotlin
import androidx.compose.runtime.*
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.renderer.Markdown

var text by remember { mutableStateOf("") }
var generating by remember { mutableStateOf(true) }

LaunchedEffect(Unit) {
    try {
        llmTokenFlow.collect { text += it }
    } finally {
        generating = false
    }
}

Markdown(
    markdown = text,
    isStreaming = generating,
    parserConfig = MarkdownParserConfig.LlmStreaming,
)
```

`llmTokenFlow` 由应用提供。`LlmStreaming` 会合并短片段追加，并在换行时提前更新。
生成过程中，组件可以展示尚未闭合的代码围栏、公式和强调语法；清空、替换或重试输入时，
继续更新同一个组件即可。

## 外观与配置

所有配置均可省略，默认配色跟随系统明暗主题。

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.markdown.renderer.*

Markdown(
    markdown = text,
    colors = MarkdownDefaults.colors(link = Color(0xFF0969DA)),
    typography = MarkdownTypography(
        text = TextStyle(fontSize = 16.sp, lineHeight = 28.sp),
    ),
    layout = MarkdownLayout(blockSpacing = 12.dp),
)
```

可通过 `MarkdownDefaults.lightColors()`、`darkColors()` 或 `material3Colors()` 选择配色。
`ProvideMarkdownDefaults` 设置应用级默认值，组件参数优先。

| 需求 | API |
|---|---|
| 语法方言、Emoji、诊断 | `parserConfig = MarkdownParserConfig(...)` |
| 保留软换行、标题编号 | `softBreakPolicy = MarkdownSoftBreakPolicy.LineBreak`、`enableHeadingNumbering` |
| 滚动、选择、生成状态 | `enableScroll`、`enableSelection`、`isStreaming` |
| 查找与定位正文 | `textSearchState = rememberMarkdownTextSearchState()` |
| 点击回调、语义文本装饰 | `interactions = MarkdownInteractions(...)` |
| 自定义选区菜单、图片、头尾与块工具栏 | `components = MarkdownComponents(...)` |
| 代码、公式、图表样式 | `codeColors`、`codeTypography`、`codeLayout`、`latexConfig`、`diagramTheme` |
| 自定义语法与渲染 | `directivePlugins` |
| 控制滚动、清除选区 | `state = rememberMarkdownState()` |

代码、LaTeX 和图表使用各自上游库的原生配置类型。
完整示例、字体配置与交互行为见[使用指南](docs/USAGE_zh.md)。

## 语法与 HTML

默认使用启用全部已支持扩展的 `ExtendedFlavour`；也可选择 `CommonMarkFlavour`、
`GFMFlavour` 或 `MarkdownExtraFlavour` 限定语法范围。Compose 渲染与 HTML 导出共用解析配置：

```kotlin
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.parser.flavour.GFMFlavour
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownHtml

val config = MarkdownParserConfig(flavour = GFMFlavour)
Markdown(markdown = text, parserConfig = config)
val html = MarkdownHtml.render(markdown = text, parserConfig = config)
```

| 语法 | 示例 |
|---|---|
| 标准 Markdown | 标题、段落、引用、列表、链接、图片、代码与强调 |
| GFM | 表格、任务列表、删除线与自动链接 |
| 公式与图表 | `$...$`、`$$...$$`，以及 Mermaid / PlantUML / DOT 围栏块 |
| 引用与导航 | `[^footnote]`、`[@citation]`、`[[wiki link]]` 与 `[TOC]` |
| 语义文本 | `==highlight==`、上下标、`++inserted++`、折叠文本、注音与 Emoji |
| 结构化内容 | 提示框、定义列表、Front Matter、容器、多栏与标签页 |
| 属性与插件 | `{.class #id key=value}`、图片尺寸与 `{% tag args %}` 指令 |

Compose 无需 WebView 即可渲染安全的 HTML 子集，包括常见行内格式、链接、图片、容器、标题、
列表和代码块。不支持或格式错误的片段保留为可见源码。HTML 导出会透传原始 HTML；Compose 的
渲染策略不负责净化导出结果。支持的属性见 [HTML 行为](docs/USAGE_zh.md#html-行为)。

## 无障碍与验证

渲染器默认提供标题导航、段落/列表阅读单元和只读任务状态。
通过 `MarkdownParserConfig(enableLinting = true)` 启用源码诊断，可检查标题跳级、未定义脚注、
空链接及图片缺少描述等问题。这些检查不代表 WCAG 合规认证，读屏体验仍需在目标设备验证。

仓库包含 652 个 CommonMark 0.31.2 规范示例、增量/流式回归测试、独立进程极端输入检查与解析器
性能预算。[语法支持清单](markdown-parser/PARSER_COVERAGE_ANALYSIS.md) 描述已支持的行为，
不代表代码覆盖率。执行状态以 [CI 结果](https://github.com/huarangmeng/Markdown/actions/workflows/test.yml)
为准；JVM 测试、JS/Wasm 测试编译与 macOS Native 测试分别检查，Web 测试编译不代表浏览器执行。

## 更多示例

- [使用指南](docs/USAGE_zh.md)：查找、选区菜单、行内装饰、块插槽、图片与插件。
- [预览用例](markdown-preview/src/commonMain/kotlin/com/hrm/markdown/preview)：Demo 使用的功能示例。
- 渲染依赖：[LaTeX](https://github.com/huarangmeng/latex)、[codehigh](https://github.com/huarangmeng/codehigh) 与 [diagram](https://github.com/huarangmeng/diagram)。
- [贡献指南](AGENTS.md)：仓库结构与验证命令。

在仓库中运行 Demo：

```bash
./gradlew :androidapp:assembleDebug                    # Android
./gradlew :composeApp:run                             # Desktop (JVM)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun       # Web (Wasm)
./gradlew :composeApp:jsBrowserDevelopmentRun           # Web (JS)
```

iOS 请在 Xcode 中打开 `iosApp/`。

## 开源协议

[MIT](LICENSE) · Copyright (c) 2026 huarangmeng
