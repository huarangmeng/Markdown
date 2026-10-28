<div align="center">

# 🖊️ KMP Markdown

**A Blazing-Fast, Cross-Platform Markdown Engine for Compose Multiplatform**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.huarangmeng/markdown-renderer.svg?color=orange&label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.huarangmeng.markdown)
[![CommonMark](https://img.shields.io/badge/CommonMark%200.31.2-652%2F652%20✓-brightgreen)](https://spec.commonmark.org/0.31.2/)
[![Android API](https://img.shields.io/badge/Android%20API-23%2B-34A853?logo=android&logoColor=white)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

*One library. One codebase. Pixel-perfect Markdown on Android, iOS, Desktop & Web.*

[English](./README.md) · [中文](./README_zh.md)

</div>

---

## ✨ Why KMP Markdown?

The `macosArm64` library target is experimental and requires `org.jetbrains.compose.experimental.macos.enabled=true`. Native rendering also requires macOS Native Maven publications of the transitive LaTeX, codehigh and diagram libraries; existing JVM/iOS artifacts cannot substitute for them. The demo desktop application remains JVM-based. Native tests and Maven variant metadata are checked by the macOS CI job; adding this target alone does not establish successful native linking or rendering.

|  | Feature | Description |
|--|---------|-------------|
| 🚀 | **Incremental Updates** | Reuses unchanged content when editing documents or receiving streaming output |
| 🌍 | **True Cross-Platform** | One codebase renders identically on **Android**, **iOS**, **Desktop (JVM)**, **Web (Wasm/JS)**; experimental **macOS (Kotlin/Native)** target |
| 📐 | **Syntax Support** | 413 supported syntax and behavior items, **652/652 CommonMark Spec** examples passing, plus GFM & 20+ extensions |
| 🤖 | **LLM-Ready Streaming** | First-class token-by-token rendering with display-frame coalescing — zero flicker during AI generation |
| 🎨 | **Fully Themeable** | 30+ configurable properties, built-in GitHub light/dark themes, auto system detection |
| 📊 | **LaTeX Math** | Inline `$...$` and block `$$...$$` formulas via integrated LaTeX rendering engine |
| 🔍 | **Built-in Linting** | 13+ diagnostic rules including WCAG accessibility checks — catch issues at parse time |
| 🖼️ | **Image Loading** | Coil3 + Ktor3 out-of-the-box, with size specification and custom renderer support |
| 🖱️ | **Cross-Block Selection** | Select text across blocks with the system copy/translate menu |

Long-press and drag to select text. After releasing the gesture or adjusting a selection handle, tap a blank area inside the Markdown content once to clear the selection.

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
markdown = "1.5.0"

[libraries]
markdown-renderer = { module = "io.github.huarangmeng:markdown-renderer", version.ref = "markdown" }
```

Then in your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.markdown.renderer)
}
```

> 💡 This single dependency includes syntax support, plugin APIs, Coil3 + Ktor3 image loading, and Mermaid / PlantUML / DOT rendering.

### Basic Usage

```kotlin
import com.hrm.markdown.renderer.Markdown

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
    )
}
```

That's it — **3 lines** to render beautiful Markdown across all platforms.

### LaTeX configuration

Pass the native `LatexConfig` directly. Inline and block formulas share its fonts, theme, callbacks, and measurements.

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

The default is `LatexConfig()` (20.sp, system light/dark, transparent background). Markdown colors do not override it.

**We recommend keeping the bundled KaTeX fonts**: system fonts may have missing glyphs or layout differences.
If you need to omit the font resources, exclude `latex-fonts-katex`. The default configuration safely falls back to system fonts without crashing when fonts are missing:

```kotlin
implementation(libs.markdown.renderer) {
    exclude(group = "io.github.huarangmeng", module = "latex-fonts-katex")
}
```

---

## 🔌 Plugin Extensions

Use `directivePlugins` to add custom syntax such as video embeds and render it with native Compose
components. Plugins can also handle the standard `{% tag ... %}` directive syntax and HTML export.

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

Input transformers support streaming by default with
`MarkdownTransformerStreamingSupport.RestartOnRewrite`. The component automatically refreshes content
when a transformation rewrites earlier text. Declare `AppendSafe` only when appending input always
preserves the entire previous transformed output, or `Unsupported` to request full-content updates
instead of incremental streaming. `LocalMarkdownSourceMap` maps transformed positions back to the original input.

Treat plugins and transformers as configuration: reuse instances while their behavior stays the same,
and provide new instances when it changes. Replacing rendering callbacks updates the presentation
without interrupting streaming; replacing input transformers refreshes the content and source mappings.

Use `scope.directive` to read directive names, arguments, and content information in
`DirectiveBlockRenderScope` and `DirectiveInlineRenderScope`. For HTML export,
`HtmlDirectiveFallback` and `HtmlInlineDirectiveFallback` receive the corresponding directive data.

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

`LlmStreaming` batches short updates using a 16-character threshold and updates early at newlines.
Set `isStreaming = false` when generation finishes or is cancelled to display all remaining text.
Code appears as it arrives, including inside unclosed fences.

- Retry, clear, or replace the text during generation; the displayed document follows the latest input.
- Unclosed fences, math blocks, and emphasis receive temporary display completion during streaming.
- Multiple token updates in one display frame are combined into the latest document update.

Pass the latest complete text to `markdown` for both streaming output and document edits.
The component handles incremental updates and any required refreshes automatically.

When the document changes during text selection, selection endpoints and active drag handles are
clamped to valid UTF-16 character boundaries. Removing a selected block clears the selection and
drag state, so copying cannot use stale endpoints.

---

## Interaction and standard block slots

`MarkdownInteractions` groups typed link, citation (`[@key]`) and image callbacks. When no link callback is supplied, links use Compose `LocalUriHandler`.

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

The host implements `runCode`. Images use Coil by default; customize them with `components.image`. `event.gallery` provides the document image gallery.

Keep slot content within its declared height; toolbar text is excluded from Markdown copy. `MarkdownTableInfo.rowCount` includes the header row.

Set `enableScroll = false` when a parent container handles vertical scrolling.

Soft breaks default to `Space`. Use `LineBreak` to preserve line breaks in display and copy. HTML export also accepts `softBreakPolicy`.

---

## 🎨 Colors, typography, layout, and state

All settings are optional. `Markdown(markdown = text)` follows system colors. Native Markdown settings have three independent groups; code, LaTeX, and diagrams retain their native configuration types.

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
        onLinkClick = { url -> /* host navigation */ },
        onCodeTokenClick = { token -> /* codehigh CodeToken */ },
    ),
)
```

Choose palettes with `MarkdownDefaults.lightColors()`, `darkColors()`, or `material3Colors()`. `ProvideMarkdownDefaults` sets app defaults; instance arguments take precedence.

Code settings reuse codehigh's `CodeColors`, `CodeTypography`, and `CodeLayout`, so the same configuration works with both Markdown and `CodeBlock`.

| Purpose | Parameter |
|---|---|
| Source parsing: flavour, emoji, lint, append coalescing | `parserConfig = MarkdownParserConfig(...)` |
| Soft breaks and heading numbering | `softBreakPolicy`, `enableHeadingNumbering` |
| Internal scroll and selection | `enableScroll`, `enableSelection` |
| Generation in progress | `isStreaming` |
| Link, citation, image, and code-token handlers | `interactions = MarkdownInteractions(...)` |
| Header/footer, image renderer, code/table chrome, admonition icons | `components = MarkdownComponents(...)` |
| Code appearance (native codehigh types) | `codeColors`, `codeTypography`, `codeLayout` |
| LaTeX and diagram settings | `latexConfig`, `diagramTheme` |
| Directive extensions | `directivePlugins` |

`onCodeTokenClick` receives codehigh's `CodeToken`; its `range` is relative to the containing code block.

```kotlin
val state = rememberMarkdownState()
val scope = rememberCoroutineScope()
Button(onClick = { scope.launch { state.scrollToBlock(10) } }) { Text("Jump") }
Markdown(markdown = text, state = state, components = MarkdownComponents(
    header = { Text("Introduction") },
    footer = { Text("End") },
))
```

Use `rememberMarkdownState()` to control scrolling and clear selection. Block indexes exclude header/footer.

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
| **Lists** | Unordered/ordered/task lists, nested, tight/loose distinction; task checkboxes have an 8.dp gap before content |
| **Tables (GFM)** | Column alignment, inline formatting in cells, escaped pipes |
| **Thematic Breaks** | `---`, `***`, `___` |
| **HTML Blocks** | All 7 CommonMark types; safe Compose fragments for common containers, headings, quotes, code blocks, lists, and text alignment |
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

> These rules check selected accessibility-related source issues; they do not certify full WCAG compliance.

### Assistive technology semantics

The Compose renderer provides basic reading semantics by default, independently of `enableLinting`:

- ATX, Setext, and supported safe HTML headings expose heading navigation markers.
- Inline paint fragments created by wrapping and styling merge into paragraph or list-item reading units in source order. Links and interactive widgets retain native actions.
- Lists expose collection and item positions. Task items associate their text with checked state as read-only checkboxes, without an editing action.

These semantics are supplied by the shared Compose rendering path. Platform screen readers, including TalkBack and VoiceOver, still need validation on target devices. The preview includes a mixed-content example under “渲染与质量 → 无障碍阅读”.

---

## 🧪 Spec Compliance

| Spec | Status |
|------|--------|
| [CommonMark 0.31.2](https://spec.commonmark.org/0.31.2/) | **652/652 (100%)** ✅ |
| [GFM 0.29](https://github.github.com/gfm/) | Tables, task lists, strikethrough, autolinks ✅ |
| [Markdown Extra](https://michelf.ca/projects/php-markdown/extra/) | Footnotes, definition lists, abbreviations, fenced code ✅ |

### Reliability

| Area | Verified behavior |
|------|-------------------|
| Incremental and streaming parsing | Matches independent full parsing across four flavours, including continuous edits and streamed prefixes |
| Asynchronous lifecycle | Regression coverage for full-text replacement, cancellation, configuration changes, and page disposal |
| Extreme inputs | Isolated-process checks pass for 512 nested quotes, million-character lines, dense delimiters, and large unclosed blocks |
| Regression checks | JVM tests and parser performance budgets pass; parser and renderer tests compile for JS and Wasm |

See the [CI results](https://github.com/huarangmeng/Markdown/actions/workflows/test.yml) for the current revision.

### Flavour System

Choose a syntax profile through `parserConfig`:

| Flavour | Syntax |
|---|---|
| `CommonMarkFlavour` | Strict CommonMark |
| `GFMFlavour` | CommonMark, tables, task lists, and strikethrough |
| `MarkdownExtraFlavour` | Footnotes, definition lists, abbreviations, and tables |
| `ExtendedFlavour` | All supported extensions; the default |

Share one configuration between the component and HTML export:

```kotlin
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.parser.flavour.GFMFlavour
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownHtml

val config = MarkdownParserConfig(flavour = GFMFlavour)
Markdown(markdown = input, parserConfig = config)
val html = MarkdownHtml.render(markdown = input, parserConfig = config)
```

Configurations are immutable and preserve the supplied emoji aliases. Use `copy` to change settings,
or `with` to add syntax starters or processors while retaining the other syntax settings.
For example, this configuration limits heading levels in both display and export:

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

`BlockPostProcessor` transforms independent blocks: return the original block after modifying it,
a replacement block, or `null` to remove it. Use `PostProcessor` for changes that depend on the whole
document. The component applies these transformations automatically when content changes.
Reuse configurations and extension instances while their behavior remains unchanged.

### Safe HTML in Compose

`Markdown()` renders a safe, cross-platform subset of HTML without a WebView:

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
`header`, `footer`, `h1`-`h6`, `blockquote`, `hr`, safe `pre`/`code` code blocks, and safe
`ul`/`ol`/`li` list structures. Containers can contain the safe inline tags above and use
`align` or the limited `style="text-align:..."` form with `left`, `start`, `center`,
`right`, or `end`. `ol` accepts an integer `start` attribute; `ul` and `li` accept no
attributes. `pre` can contain text directly or a single `code` child; `code` accepts only
`class="language-..."` or `class="lang-..."` to declare a language. `left`/`right` remain
physical directions, while `start`/`end` follow the current layout direction. Compose
routing is explicit by CommonMark HTML block type: comments (type 2) may be hidden, safe
type 6/7 fragments may be mapped, and safe type 1 `pre` code fragments may be mapped;
other type 1 fragments and types 3/4/5 stay raw. Malformed fragments, unknown attributes,
or unsupported structures such as tables, forms, scripts, and arbitrary CSS fall back
atomically to visible raw HTML.

A block tag must start on its own line according to CommonMark rules. In particular,
`text <p>block</p> text` is invalid block markup and remains inline source; put the `p` element
on its own line. `MarkdownHtml.render` continues to pass raw HTML through when exporting HTML.

---

## 🖼️ Custom Image Rendering

Built-in Coil3 handles images automatically. Need custom rendering? Easy:

```kotlin
Markdown(
    markdown = markdownText,
    components = MarkdownComponents(image = { data, modifier ->
        // data.url, data.altText, data.width, data.height available
        AsyncImage(
            model = data.url,
            contentDescription = data.altText,
            modifier = modifier,
        )
    }),
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

---

## 📊 Feature Support Summary

| # | Category | Supported / Listed |
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
| LaTeX math | `io.github.huarangmeng:latex-base`, `io.github.huarangmeng:latex-parser`, `io.github.huarangmeng:latex-renderer`, `io.github.huarangmeng:latex-fonts-katex` | [huarangmeng/latex](https://github.com/huarangmeng/latex) |
| Code highlighting | `io.github.huarangmeng:codehighlight-parser`, `io.github.huarangmeng:codehighlight-render` | [huarangmeng/codehigh](https://github.com/huarangmeng/codehigh) |
| Diagram blocks | `io.github.huarangmeng:diagram-core`, `io.github.huarangmeng:diagram-layout`, `io.github.huarangmeng:diagram-parser`, `io.github.huarangmeng:diagram-render` | [huarangmeng/diagram](https://github.com/huarangmeng/diagram) |

If you only need a subset of the rendering stack, you can depend on the corresponding upstream library directly.

---

## 📄 License

```
MIT License · Copyright (c) 2026 huarangmeng
```

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
