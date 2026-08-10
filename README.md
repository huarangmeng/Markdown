<div align="center">

# 🖊️ KMP Markdown

**A Blazing-Fast, Cross-Platform Markdown Engine for Compose Multiplatform**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.huarangmeng/markdown-parser.svg?color=orange&label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.huarangmeng.markdown)
[![CommonMark](https://img.shields.io/badge/CommonMark%200.31.2-652%2F652%20✓-brightgreen)](https://spec.commonmark.org/0.31.2/)
[![Android API](https://img.shields.io/badge/Android%20API-23%2B-34A853?logo=android&logoColor=white)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

*One library. One codebase. Pixel-perfect Markdown on Android, iOS, Desktop & Web.*

[English](./README.md) · [中文](./README_zh.md)

</div>

---

## ✨ Why KMP Markdown?

|  | Feature | Description |
|--|---------|-------------|
| 🚀 | **Blazing Fast** | AST-based recursive descent parser with incremental parsing — only re-parses what changed |
| 🌍 | **True Cross-Platform** | One codebase renders identically on **Android**, **iOS**, **Desktop (JVM)**, **Web (Wasm/JS)** |
| 📐 | **100% Coverage** | 413 Markdown features, **652/652 CommonMark Spec** tests passing, plus GFM & 20+ extensions |
| 🤖 | **LLM-Ready Streaming** | First-class token-by-token rendering with display-frame coalescing — zero flicker during AI generation |
| 🎨 | **Fully Themeable** | 30+ configurable properties, built-in GitHub light/dark themes, auto system detection |
| 📊 | **LaTeX Math** | Inline `$...$` and block `$$...$$` formulas via integrated LaTeX rendering engine |
| 🔍 | **Built-in Linting** | 13+ diagnostic rules including WCAG accessibility checks — catch issues at parse time |
| 🖼️ | **Image Loading** | Coil3 + Ktor3 out-of-the-box, with size specification and custom renderer support |
| 🖱️ | **Cross-Block Selection** | DIY selection layer over LazyColumn — continuous selection across blocks, reuses the system copy/translate menu, no first-frame stall on huge docs |

---

## 🎬 See It in Action

### 🤖 LLM Streaming Rendering

Real-time token-by-token output with incremental parsing — no flicker, no re-render.

<p align="center">
  <img src="./images/llm_stream.png" width="260" alt="LLM Streaming Rendering Demo" />
</p>

### 🔍 Syntax Diagnostics & Linting

Built-in linting with WCAG accessibility checks — heading jumps, broken footnotes, empty links, and more.

<p align="center">
  <img src="./images/Diagnostic.png" width="260" alt="Markdown Diagnostics & Linting" />
</p>

### 🌐 Rich HTML & Extension Support

Full CommonMark HTML parsing/export, safe Compose rendering for common inline and container HTML tags, GFM tables, admonitions, math, code highlighting, and 20+ extensions.

<p align="center">
  <img src="./images/html_support.png" width="260" alt="HTML & Extension Support" />
</p>

---

## 🚀 Quick Start

### Installation

Add to your `gradle/libs.versions.toml`:

```toml
[versions]
markdown = "1.2.1"

[libraries]
markdown-parser = { module = "io.github.huarangmeng:markdown-parser", version.ref = "markdown" }
markdown-runtime = { module = "io.github.huarangmeng:markdown-runtime", version.ref = "markdown" }
markdown-renderer = { module = "io.github.huarangmeng:markdown-renderer", version.ref = "markdown" }
```

Then in your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.markdown.parser)
    implementation(libs.markdown.runtime)
    implementation(libs.markdown.renderer)
}
```

> 💡 `markdown-renderer` bundles Coil3 + Ktor3 for image loading, and `diagram-render` for Mermaid / PlantUML / DOT blocks, as transitive dependencies.

### Basic Usage

```kotlin
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.codehigh.theme.OneDarkProTheme

@Composable
fun MyScreen() {
    Markdown(
        markdown = """
            # Hello World
            
            This is a paragraph with **bold** and *italic* text.
            
            - Item 1
            - Item 2
            
            ```kotlin
            fun hello() = println("Hello")
            ```
        """.trimIndent(),
        modifier = Modifier.fillMaxSize(),
        theme = MarkdownTheme.auto(), // Follows system light/dark mode
        codeTheme = OneDarkProTheme,  // Optional: pass a codehigh theme directly
    )
}
```

That's it — **3 lines** to render beautiful Markdown across all platforms.

---

## 🔌 Plugin Extensions

This project ships a plugin-friendly architecture based on:

- An official directive gateway (`{% tag ... %}`) parsed by `markdown-parser`
- A runtime input transform pipeline to normalize custom syntax into directives
- Renderer-side dispatch that maps directives (e.g. `video`) to native Compose blocks

Basic usage:

```kotlin
Markdown(
    markdown = """
Custom syntax:

!VIDEO[Demo](https://cdn.example.com/a.mp4){poster=https://cdn.example.com/a.jpg}
    """.trimIndent(),
    directivePlugins = listOf(VideoDirectivePlugin),
)
```

Plugin skeleton:

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

`DirectiveBlockRenderScope` and `DirectiveInlineRenderScope` are snapshot-based.
Use `scope.directive` as the single structured entry point for directive data.
For HTML export, `HtmlDirectiveFallback` and `HtmlInlineDirectiveFallback` also receive
snapshot objects instead of parser AST nodes.

HTML export uses the same directive pipeline:

```kotlin
val html = MarkdownHtml.render(
    markdown = markdown,
    directivePlugins = listOf(VideoDirectivePlugin),
)
```

---

## 🤖 LLM Streaming Integration

Purpose-built for AI/LLM scenarios. Enable `isStreaming` for flicker-free incremental rendering:

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
    isStreaming = isStreaming,
    config = MarkdownConfig.LlmStreaming, // 16-char parser coalescing + frame-coalesced UI commits
)
```

**What happens under the hood:**
- ✅ Only re-parses the "dirty" tail region — stable blocks are reused
- ✅ Validates the append-only prefix — retry, clear, or replacement updates restart atomically
- ✅ Normalizes CRLF across token/chunk boundaries and flushes buffered tails on cancellation
- ✅ Auto-closes unclosed fences, math blocks, emphasis during streaming
- ✅ Lazy inline parsing — block structure first, inline elements on demand
- ✅ FNV-1a content hashing for O(1) block stability detection
- ✅ Display-frame coalescing — multiple token updates in one frame commit only the latest document, without an artificial low-fps cap

The `Markdown(document = ...)` overload also supports mutable custom ASTs: nodes without source
ranges receive deterministic tree-path identities, and rendered semantic fields participate in
cache invalidation.

---

## 🎨 Theming

```kotlin
// Built-in themes
Markdown(markdown = text, theme = MarkdownTheme.light())  // GitHub Light
Markdown(markdown = text, theme = MarkdownTheme.dark())   // GitHub Dark
Markdown(markdown = text, theme = MarkdownTheme.auto())   // Auto-detect

// Full customization (30+ properties)
Markdown(
    markdown = text,
    theme = MarkdownTheme(
        headingStyles = listOf(
            TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
            // h2 ~ h6 ...
        ),
        bodyStyle = TextStyle(fontSize = 16.sp),
        codeBlockBackground = Color(0xFFF5F5F5),
        // ...and much more
    ),
    onLinkClick = { url -> /* handle click */ },
)
```

Code highlighting and code block/inline code theming are provided by the `codehigh` library. `markdown-renderer` no longer bundles its own regex-based highlighter.

```kotlin
import com.hrm.codehigh.theme.DraculaProTheme
import com.hrm.codehigh.theme.LocalCodeTheme
import com.hrm.codehigh.theme.OneDarkProTheme

// Option 1: Pass codehigh theme per Markdown call
Markdown(
    markdown = text,
    theme = MarkdownTheme.auto(),
    codeTheme = OneDarkProTheme,
)

// Option 2: Provide a default codehigh theme globally via CompositionLocal
CompositionLocalProvider(
    LocalCodeTheme provides DraculaProTheme
) {
    Markdown(
        markdown = text,
        theme = MarkdownTheme.auto(),
    )
}
```

- `theme` controls general Markdown UI (headings, body, tables, quotes, math, etc.)
- `codeTheme` controls code highlighting and styling for code blocks and inline code
- If `codeTheme` is not provided, the default from `codehigh` is used

---

## 📐 Comprehensive Syntax Support (413 Features)

<details>
<summary><b>📦 Block Elements</b> — Everything you need for structured content</summary>

| Feature | Details |
|---------|---------|
| **Headings** | ATX (`# ~ ######`), Setext (`===`/`---`), custom IDs (`{#id}`), auto-generated anchors |
| **Paragraphs** | Multi-line merging, blank line separation, lazy continuation |
| **Code Blocks** | Fenced (`` ``` ``/`~~~`) with language highlight (20+ languages), indented, line numbers, line highlighting |
| **Block Quotes** | Nested, lazy continuation, inner block elements |
| **Lists** | Unordered/ordered/task lists, nested, tight/loose distinction |
| **Tables (GFM)** | Column alignment, inline formatting in cells, escaped pipes |
| **Thematic Breaks** | `---`, `***`, `___` |
| **HTML Blocks** | All 7 CommonMark types; safe Compose fragments for common containers and text alignment |
| **Link Reference Definitions** | Full support with title variants |

</details>

<details>
<summary><b>✏️ Inline Elements</b> — Rich text formatting at your fingertips</summary>

| Feature | Details |
|---------|---------|
| **Emphasis** | Bold, italic, bold-italic, nested, CJK-aware delimiter rules |
| **Strikethrough** | `~~text~~`, `~text~` |
| **Inline Code** | Single/multi backtick, space stripping |
| **Links** | Inline, reference (full/collapsed/shortcut), autolinks, GFM bare URLs, attribute blocks |
| **Images** | Inline, reference, `=WxH` size specification, attribute blocks, auto Figure conversion |
| **Inline HTML** | CommonMark tags/comments/CDATA/processing instructions; safe Compose semantics for common formatting, links, breaks, and images |
| **Escapes & Entities** | 32 escapable characters, named/numeric HTML entities |
| **Line Breaks** | Hard (spaces/backslash), soft |

</details>

<details>
<summary><b>🔌 Extensions</b> — Power features beyond standard Markdown</summary>

| Feature | Syntax |
|---------|--------|
| **Math (LaTeX)** | `$...$` inline, `$$...$$` block, `\tag{}`, `\ref{}` |
| **Footnotes** | `[^label]` references, multi-line definitions, block content |
| **Admonitions** | `> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`, `> [!CAUTION]` |
| **Highlight** | `==text==` |
| **Super/Subscript** | `^text^`, `~text~`, `<sup>`, `<sub>` |
| **Insert Text** | `++text++` |
| **Emoji** | `:smile:` shortcodes (200+), ASCII emoticons (40+), custom mappings |
| **Definition Lists** | Term + `: definition` format |
| **Front Matter** | YAML (`---`) and TOML (`+++`) |
| **TOC** | `[TOC]` with depth, exclude, ordering options |
| **Custom Containers** | `:::type` with nesting, CSS classes, IDs |
| **Diagram Blocks** | Mermaid, PlantUML, Graphviz, and more |
| **Multi-Column Layout** | `:::columns` with percentage/pixel widths |
| **Tab Blocks** | `=== "Tab Title"` MkDocs Material style |
| **Directives** | `{% tag args %}...{% endtag %}` with positional/keyword args |
| **Spoiler Text** | `>!hidden text!<` Discord/Reddit style |
| **Wiki Links** | `[[page]]`, `[[page\|display text]]` |
| **Ruby Text** | `{漢字\|かんじ}` pronunciation annotations |
| **Bibliography** | `[@key]` citations with `[^bibliography]` definitions |
| **Block Attributes** | `{.class #id key=value}` kramdown/Pandoc style |
| **Page Breaks** | `***pagebreak***` for print/PDF export |
| **Styled Text** | `[text]{.red style="color:red"}` inline CSS |

</details>

---

## 🔍 Built-in Linting & Diagnostics

Enable once, catch problems everywhere:

```kotlin
val parser = MarkdownParser(enableLinting = true)
val document = parser.parse(markdown)

document.diagnostics.forEach { diagnostic ->
    println("Line ${diagnostic.line}: [${diagnostic.severity}] ${diagnostic.message}")
}
```

**13+ diagnostic rules:**

| Rule | Severity | Description |
|------|----------|-------------|
| Heading level skip | ⚠️ WARNING | h1 → h3 without h2 |
| Duplicate heading ID | ⚠️ WARNING | Multiple headings generate the same anchor |
| Invalid footnote ref | ❌ ERROR | Reference to undefined footnote |
| Unused footnote | ⚠️ WARNING | Footnote defined but never referenced |
| Empty link target | ⚠️ WARNING | `[text]()` with no URL |
| Missing alt text | ⚠️ WARNING | Images without description |
| Empty link text | ⚠️ WARNING | Links invisible to screen readers |
| Non-descriptive link | ⚠️ WARNING | "click here", "read more" links |
| Missing code language | ℹ️ INFO | Fenced code without language tag |
| Table missing header | ⚠️ WARNING | Screen readers need `<th>` |
| Long alt text | ⚠️ WARNING | Alt text > 125 characters |

> Follows [WCAG 2.1 AA](https://www.w3.org/TR/WCAG21/) standards for accessibility compliance.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Your Compose App                        │
├─────────────────────────────────────────────────────────────┤
│  markdown-preview                                           │
│  Interactive demo & showcase                                │
├─────────────────────────────────────────────────────────────┤
│  markdown-renderer                                          │
│  AST → Compose UI / HTML                                    │
│  Block/Inline renderers, theme, images                      │
├─────────────────────────────────────────────────────────────┤
│  markdown-runtime                                           │
│  Directive plugins / input transforms / runtime pipeline    │
├─────────────────────────────────────────────────────────────┤
│  markdown-parser                                            │
│  Markdown → AST                                             │
│  Streaming / Incremental / Flavour system                   │
│  Linting / Diagnostics / Post-processors                    │
└─────────────────────────────────────────────────────────────┘
```

| Module | Description |
|--------|-------------|
| `:markdown-parser` | Core parsing engine — Markdown string → AST. Streaming, incremental, multi-flavour. |
| `:markdown-runtime` | Directive runtime layer — plugin registry, input transforms, directive pipeline, source maps. |
| `:markdown-renderer` | Rendering engine — AST → Compose UI. Theming, image loading, code highlighting. |
| `:markdown-preview` | Interactive showcase — categorized demo of all supported features. |
| `:composeApp` | Cross-platform demo app (Android/iOS/Desktop/Web). |
| `:androidApp` | Android-specific demo app. |

---

## 🧪 Spec Compliance

| Spec | Status |
|------|--------|
| [CommonMark 0.31.2](https://spec.commonmark.org/0.31.2/) | **652/652 (100%)** ✅ |
| [GFM 0.29](https://github.github.com/gfm/) | Tables, task lists, strikethrough, autolinks ✅ |
| [Markdown Extra](https://michelf.ca/projects/php-markdown/extra/) | Footnotes, definition lists, abbreviations, fenced code ✅ |

### Flavour System

Configure which syntax features to enable:

```kotlin
// Strict CommonMark — no extensions
val doc = MarkdownParser(CommonMarkFlavour).parse(input)

// GFM — CommonMark + tables, strikethrough, autolinks
val doc = MarkdownParser(GFMFlavour).parse(input)

// Extended (default) — everything enabled
val doc = MarkdownParser().parse(input)

// One-shot HTML rendering
val html = HtmlRenderer.renderMarkdown(input, flavour = CommonMarkFlavour)
```

### Safe HTML in Compose

`Markdown()` renders a safe, cross-platform subset of inline HTML without a WebView:

```markdown
Text with <strong>bold</strong>, <em>italic</em>,
<span style="color:red">styled text</span>, and <a href="https://example.com">a link</a>.<br>
The next line can also contain an <img src="https://example.com/icon.png" alt="icon">.
```

Supported semantic tags are `strong`/`b`, `em`/`i`, `del`/`s`/`strike`, `mark`,
`sup`, `sub`, `ins`, `u`, `code`, `kbd`, `span`, `a`, `br`, and `img`. Inline HTML
comments are hidden. Formatting tags and `br` accept no attributes; `span` accepts only
fully supported `style`/`class` values; `a` accepts only `href`; and `img` accepts only
`src`, `alt`, `title`, `width`, and `height`. Supported `span` CSS is limited to colors,
background colors, font weight/style, and underline/line-through. Any unknown attribute,
CSS declaration, unsafe URL, mismatched tag, or unclosed tag causes that HTML source to
remain visible instead of being partially rendered or silently losing content.

Safe block fragments are supported for `p`, `div`, `center`, `article`, `section`, `main`,
`header`, and `footer`. They can contain the safe inline tags above and use `align` or the
limited `style="text-align:..."` form with `left`, `start`, `center`, `right`, or `end`.
`left`/`right` remain physical directions, while `start`/`end` follow the current layout
direction. Compose routing is explicit by CommonMark HTML block type: comments (type 2)
may be hidden and safe type 6/7 fragments may be mapped; opaque types 1/3/4/5 stay raw.
Malformed fragments, unknown attributes, or unsupported structures such as tables, forms,
scripts, and arbitrary CSS fall back atomically to visible raw HTML.

A block tag must start on its own line according to CommonMark rules. In particular,
`text <p>block</p> text` is invalid block markup and remains inline source; put the `p` element
on its own line. `HtmlRenderer`/`MarkdownHtml` continue to pass all raw HTML through when exporting HTML.

---

## 🖼️ Custom Image Rendering

Built-in Coil3 handles images automatically. Need custom rendering? Easy:

```kotlin
Markdown(
    markdown = markdownText,
    imageContent = { data, modifier ->
        // data.url, data.altText, data.width, data.height available
        AsyncImage(
            model = data.url,
            contentDescription = data.altText,
            modifier = modifier,
        )
    },
)
```

Supports size specification in Markdown:
```markdown
![Photo](https://example.com/photo.png =400x300)
![Auto width](https://example.com/photo.png =x200)
```

---

## ▶️ Running the Demo

```bash
# Android
./gradlew :composeApp:assembleDebug

# Desktop (JVM)
./gradlew :composeApp:run

# Web (Wasm)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web (JS)
./gradlew :composeApp:jsBrowserDevelopmentRun

# iOS — open iosApp/ in Xcode
```

### Running Tests

```bash
./gradlew :markdown-parser:jvmTest      # Parser tests
./gradlew :markdown-renderer:jvmTest     # Renderer tests
./gradlew jvmTest                        # All tests

# Forked parser performance gate (p95, throughput, allocation, peak heap)
./gradlew :markdown-benchmark:performanceGate

# Real Compose startup, long-document scrolling, and cross-block selection
# Run on a physical Android device with the benchmark build variant.
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Performance budgets live in `markdown-benchmark/performance-baseline.json`. See
[`markdown-benchmark/README.md`](markdown-benchmark/README.md) for reproducible runs and baseline
update rules.

---

## 📊 Coverage Summary

| # | Category | Coverage |
|---|----------|----------|
| 1 | Headings | 17/17 (100%) |
| 2 | Paragraphs | 5/5 (100%) |
| 3 | Code Blocks | 17/17 (100%) |
| 4 | Block Quotes | 8/8 (100%) |
| 5 | Lists | 20/20 (100%) |
| 6 | Thematic Breaks | 6/6 (100%) |
| 7 | Tables (GFM) | 11/11 (100%) |
| 8 | HTML Blocks | 12/12 (100%) |
| 9 | Link References | 12/12 (100%) |
| 10 | Block Extensions | 85/85 (100%) |
| 11 | Emphasis | 13/13 (100%) |
| 12 | Strikethrough | 4/4 (100%) |
| 13 | Inline Code | 8/8 (100%) |
| 14 | Links | 27/27 (100%) |
| 15 | Images | 17/17 (100%) |
| 16 | Inline HTML | 10/10 (100%) |
| 17 | Escapes & Entities | 10/10 (100%) |
| 18 | Line Breaks | 5/5 (100%) |
| 19 | Inline Extensions | 50/50 (100%) |
| 20 | Streaming Engine | 27/27 (100%) |
| 21 | Character & Encoding | 10/10 (100%) |
| 22 | HTML Generator | 12/12 (100%) |
| 23 | Linting / WCAG | 19/19 (100%) |
| 24 | Directives | 8/8 (100%) |
| | **Total** | **413/413 (100%)** |

> 📖 Full details: [PARSER_COVERAGE_ANALYSIS.md](./markdown-parser/PARSER_COVERAGE_ANALYSIS.md)

---

## 🔗 Rendering Library Guide

`markdown-renderer` integrates the following external libraries for math, code highlighting, and diagrams:

| Capability | Maven modules used by this project | Repository |
|------------|-----------------------------------|------------|
| LaTeX math | `io.github.huarangmeng:latex-base`, `io.github.huarangmeng:latex-parser`, `io.github.huarangmeng:latex-renderer` | [huarangmeng/latex](https://github.com/huarangmeng/latex) |
| Code highlighting | `io.github.huarangmeng:codehighlight-parser`, `io.github.huarangmeng:codehighlight-render` | [huarangmeng/codehigh](https://github.com/huarangmeng/codehigh) |
| Diagram blocks | `io.github.huarangmeng:diagram-core`, `io.github.huarangmeng:diagram-layout`, `io.github.huarangmeng:diagram-parser`, `io.github.huarangmeng:diagram-render` | [huarangmeng/diagram](https://github.com/huarangmeng/diagram) |

If you only need a subset of the rendering stack, you can depend on the corresponding upstream library directly.

---

## 📄 License

```
MIT License · Copyright (c) 2026 huarangmeng
```

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
