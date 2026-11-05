<div align="center">

# KMP Markdown

**Markdown rendering for Compose Multiplatform, with incremental updates and LLM streaming.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.huarangmeng/markdown-renderer.svg?color=orange&label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.huarangmeng.markdown)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Android · iOS · Desktop (JVM) · Web (Wasm/JS) · experimental macOS Native

[English](README.md) · [中文](README_zh.md) · [Usage guide](docs/USAGE.md)

</div>

## What it supports

| Capability | Includes |
|---|---|
| Markdown and extensions | CommonMark, GFM tables and task lists, footnotes, admonitions, TOC, tabs, and custom containers |
| Rich content | LaTeX math, syntax-highlighted code, Mermaid / PlantUML / DOT diagrams, and Coil3 image loading |
| Streaming and editing | Incremental updates, temporary completion of unfinished syntax, and coalesced streaming updates |
| Reading interactions | Cross-block selection, custom selection menus, link/image/citation actions, and semantic inline decorations |
| Document search | Literal text matching, scrolling to results, and independent active/all-result highlighting |
| Customization | Colors, typography, layout, component slots, and directive plugins |
| Export and diagnostics | HTML export with shared parser settings, plus optional source diagnostics |

<p align="center">
  <img src="images/llm_stream.png" width="220" alt="LLM streaming" />
  <img src="images/Diagnostic.png" width="220" alt="Source diagnostics" />
  <img src="images/html_support.png" width="220" alt="HTML and extensions" />
</p>

## Quick start

Add the renderer to your version catalog and shared module:

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

The renderer includes the parser, plugin APIs, image loading, math, code highlighting, and diagrams.
Applications do not need the preview or demo modules.

This documentation describes the current source. Search, custom selection menus, and inline decorations
were added after the `release-1.6.2` tag; use a release containing them or build from source.

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

Internal scrolling is enabled by default and needs a bounded height. Set `enableScroll = false`
when a parent container handles vertical scrolling. Text selection is enabled by default;
after selecting, tap a blank area within the content to clear it.

### Platform notes

Android requires API 23+. The desktop demo uses JVM. The experimental `macosArm64` library target
requires `org.jetbrains.compose.experimental.macos.enabled=true` and macOS Native publications of
LaTeX (including `latex-fonts-katex`), codehigh, and diagram. JVM or iOS artifacts cannot replace
missing macOS publications. Target declarations alone do not establish successful native rendering;
check the native test results for the revision you use.

## Streaming output

Pass the accumulated full text on every update, and set `isStreaming = false` when generation
finishes or is cancelled:

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

`llmTokenFlow` is your app's token stream. `LlmStreaming` batches short appends and updates early
at newlines. The component can display unfinished fences, formulas, and emphasis while receiving
text. Clearing, replacing, or retrying the input updates the same component.

## Appearance and configuration

All settings are optional. The default palette follows the system light/dark theme.

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

Use `MarkdownDefaults.lightColors()`, `darkColors()`, or `material3Colors()` for palettes.
`ProvideMarkdownDefaults` sets app-wide defaults; instance arguments take precedence.

| Need | API |
|---|---|
| Syntax profile, emoji, diagnostics | `parserConfig = MarkdownParserConfig(...)` |
| Preserve soft line breaks or number headings | `softBreakPolicy = MarkdownSoftBreakPolicy.LineBreak`, `enableHeadingNumbering` |
| Scroll, select, or track generation | `enableScroll`, `enableSelection`, `isStreaming` |
| Find and reveal text | `textSearchState = rememberMarkdownTextSearchState()` |
| Handle clicks or decorate semantic text | `interactions = MarkdownInteractions(...)` |
| Custom selection menu, images, headers, or block toolbars | `components = MarkdownComponents(...)` |
| Style code, math, or diagrams | `codeColors`, `codeTypography`, `codeLayout`, `latexConfig`, `diagramTheme` |
| Add custom syntax and rendering | `directivePlugins` |
| Control scrolling or clear selection | `state = rememberMarkdownState()` |

Code, LaTeX, and diagram settings use their respective libraries' native types. See the
[usage guide](docs/USAGE.md) for examples, font configuration, and interaction behavior.

## Syntax and HTML

The default `ExtendedFlavour` enables all supported extensions. Choose `CommonMarkFlavour`,
`GFMFlavour`, or `MarkdownExtraFlavour` for a narrower syntax profile. Compose rendering and
HTML export accept the same parser configuration:

```kotlin
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.parser.flavour.GFMFlavour
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownHtml

val config = MarkdownParserConfig(flavour = GFMFlavour)
Markdown(markdown = text, parserConfig = config)
val html = MarkdownHtml.render(markdown = text, parserConfig = config)
```

| Syntax | Examples |
|---|---|
| Standard Markdown | Headings, paragraphs, quotes, lists, links, images, code, and emphasis |
| GFM | Tables, task lists, strikethrough, and autolinks |
| Math and diagrams | `$...$`, `$$...$$`, and fenced Mermaid / PlantUML / DOT blocks |
| References | `[^footnote]`, `[@citation]`, `[[wiki link]]`, and `[TOC]` |
| Semantic text | `==highlight==`, super/subscript, `++inserted++`, spoilers, ruby, and emoji |
| Structured content | Admonitions, definition lists, front matter, containers, columns, and tabs |
| Attributes and plugins | `{.class #id key=value}`, image dimensions, and `{% tag args %}` directives |

Compose renders a safe HTML subset without a WebView, including common inline formatting,
links, images, containers, headings, lists, and code blocks. Unsupported or malformed fragments
remain visible as source. HTML export passes raw HTML through; the Compose rendering policy is
not an HTML export sanitizer. See [HTML behavior](docs/USAGE.md#html-behavior) for supported attributes.

## Accessibility and validation

The renderer supplies heading navigation, paragraph/list reading units, and read-only task states.
Enable source diagnostics with `MarkdownParserConfig(enableLinting = true)` to check issues such
as heading jumps, unresolved footnotes, empty links, and missing image descriptions. These checks
do not certify WCAG compliance; screen-reader behavior needs validation on target devices.

The repository includes 652 CommonMark 0.31.2 examples, incremental/streaming regression tests,
isolated extreme-input checks, and parser performance budgets. The
[syntax checklist](markdown-parser/PARSER_COVERAGE_ANALYSIS.md) describes supported behavior;
it is not measured code coverage. Check [CI results](https://github.com/huarangmeng/Markdown/actions/workflows/test.yml)
for execution status. JVM tests, JS/Wasm test compilation, and macOS Native tests are separate checks;
Web test compilation does not establish browser execution.

## More examples

- [Usage guide](docs/USAGE.md): search, selection menus, inline decorations, block slots, images, and plugins.
- [Preview cases](markdown-preview/src/commonMain/kotlin/com/hrm/markdown/preview): examples used by the demo apps.
- Rendering libraries: [LaTeX](https://github.com/huarangmeng/latex), [codehigh](https://github.com/huarangmeng/codehigh), and [diagram](https://github.com/huarangmeng/diagram).
- [Contributor guide](AGENTS.md): repository structure and verification commands.

Run a demo from the repository:

```bash
./gradlew :androidapp:assembleDebug                    # Android
./gradlew :composeApp:run                             # Desktop (JVM)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun       # Web (Wasm)
./gradlew :composeApp:jsBrowserDevelopmentRun           # Web (JS)
```

For iOS, open `iosApp/` in Xcode.

## License

[MIT](LICENSE) · Copyright (c) 2026 huarangmeng
