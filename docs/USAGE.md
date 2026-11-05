# Usage guide

[README](../README.md) · [中文](USAGE_zh.md)

Examples describe the current source; see the README's release note before using newer APIs.
UI snippets belong inside a composable unless a declaration is shown. They use
`import com.hrm.markdown.renderer.*` and standard Compose imports; `text` is your document.

- [Clicks and source attributes](#clicks-and-source-attributes)
- [Custom selection menus](#custom-selection-menus)
- [Document search](#document-search)
- [Inline decorations](#inline-decorations)
- [Block slots and images](#block-slots-and-images)
- [Code and math styles](#code-and-math-styles)
- [Directive plugins](#directive-plugins)
- [Parser transformations](#parser-transformations)
- [HTML behavior](#html-behavior)

## Clicks and source attributes

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

Without `onLinkClick`, links open through Compose's `LocalUriHandler`. The image gallery is built
only when read, preserves document order, and distinguishes repeated URLs. A supplied `onImageClick`
takes precedence over unified inline image events; otherwise images can reach `onInlineElementClick`
with `kind = InlineElementKinds.Image` and an `image` event containing gallery access.

Inline events describe a rendered text fragment, so nested formatting can split one semantic range
into several fragments. `payload` contains interpreted semantics, such as a citation key;
`attributes` carries supported source attributes for host conventions, such as
`<mark data-id="note-1">text</mark>`. Pure bold/italic/strikethrough marks do not produce their own
semantic click events. `anchorBoundsInWindow` is in pixels and may be absent.
Code-token ranges are relative to their code block.

## Custom selection menus

Use `components.selectionToolbar` to replace the platform toolbar:

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

`SelectionMenu` is a host-provided composable: use `info.boundsInWindow` to position it.
`info.text` contains the selected plain text; `info.ranges` contains each block's text,
`blockStableId`, half-open character range, and `blockAttributes`. Attributes carried by supported
source blocks can identify host content. This does not enable arbitrary HTML block attributes;
[HTML attribute restrictions](#html-behavior) still apply.

`copySelection()` copies and clears the selection, `selectAll()` expands it, and `dismiss()` clears
it and hides the menu. Without this slot, the platform toolbar remains in use.
`rememberMarkdownState()` also provides scroll control and selection clearing;
`state.scrollToBlock(index)` uses top-level block indexes excluding header/footer slots.

## Document search

Both `Markdown(markdown = ...)` and `Markdown(document = ...)` accept `textSearchState`.
Your app provides the query field and result controls; searching does not reparse the document,
change the user's selection, or open its menu.

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

The parent must provide a bounded height. `reveal(match)` scrolls the first matching text line into
view and returns whether usable rendered geometry was found. Repeating the call reveals the same
match again. Internal scrolling centers fully offscreen results, leaves visible results still,
and moves partially visible results to the nearest edge, subject to document boundaries.

For a parent-owned scroll container, set `enableScroll = false`. To reuse search centering, provide
this policy **before creating that scroll container** (with standard Compose foundation/runtime
imports, including `LocalBringIntoViewSpec`; opt in to `ExperimentalFoundationApi` where required):

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

Insets are the host's actual occlusion in pixels. The policy delegates ordinary focus/selection
scrolling to its `fallback` policy; selection gestures cancel pending search scrolling.

| Option | Behavior |
|---|---|
| `setQuery(query)` | Literal, non-overlapping matches in document order; case-insensitive by default |
| `setQuery(query, ignoreCase = false)` | Case-sensitive matching; whitespace is still trimmed/collapsed |
| `highlightColor` / `highlightActiveMatch` | Active result background; defaults to yellow at 30% opacity |
| `highlightAllMatches` / `allMatchHighlightColor` | Optional other-result backgrounds; off by default, yellow at 15% opacity |
| `clear()` | Clear query/results/highlights and cancel pending positioning |
| `textSearchState = null` | Detach search and release its indexing/geometry hooks when the panel closes |

The active highlight replaces the other-result color. Disabling `highlightActiveMatch` leaves
all-result highlighting available. Both styles work with `enableSelection = false`.

Search covers headings, paragraphs, nested prose, and table-cell text. It can cross bold/link
boundaries, but never crosses blocks, cells, or inline widgets. Code blocks, inline code, image alt
text, math/diagram sources, and other widget alternatives are excluded. Hidden tab content can
match logically but cannot be revealed until shown.

Matches expose `excerpt`, `blockStableId`, `documentRevision`, and `startInBlock` / `endInBlock`:
half-open UTF-16 offsets in the renderer's logical block text, **not Markdown source positions**.
Whitespace folding preserves those offsets. Case matching uses Kotlin's simple comparison,
without locale-specific folding or Unicode normalization.

Use one state per Markdown instance on the UI thread. Discard old matches when the query, document
revision, or binding changes. Newer positioning requests cancel pending requests; caller coroutine
cancellation still propagates. A query set before attachment is matched on attachment.

## Inline decorations

`inlineDecoration` receives semantic text fragments, including their `payload` and `attributes`.
Return `MarkdownInlineUnderline` for an underline and optional end marker, or `null` for no custom
decoration. This example uses a colored square; substitute a resource or vector `Painter` for an icon:

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

- Use `dashWidth` + `gapWidth` for dashes, `dotDiameter` + `dotGap` for dots, or neither for a solid line. Dots take precedence.
- A Painter marker reserves `gap + diameter` of inline width and wraps when needed. It is tinted by `color` and clipped to a square of side `diameter`.
- Each semantic range has one text endpoint, even across bold/italic fragments or line breaks. Adjacent independent ranges keep separate markers.
- Markers add no source, copied, selected, or searched characters. Updating decoration state updates layout; `endMarker = null` keeps the underline and removes the marker.
- Omitting `painter` preserves the original draw-only circle (default diameter 4.dp, gap 3.dp); that circle does not reserve inline width.

The callback also runs before layout to resolve Painter markers; keep it free of side effects and
do not assume one call per range. Markers attach to text rather than widgets, following the final
glyph's bidi direction. This does not establish full RTL paragraph support; check mixed-direction
content in your host.

## Block slots and images

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

`runCode` is a host action; `AsyncImage` comes from Coil3. Keep code/table slot content within its
declared height. Toolbar text is excluded from Markdown copy, and table row counts include the
header. Image data includes `url`, `altText`, `width`, and `height`; custom image drawing stays
separate from image click callbacks.

Images can specify dimensions: `![photo](https://example.com/photo.png =400x300)` or
`![auto width](https://example.com/photo.png =x200)`.
Set `softBreakPolicy = MarkdownSoftBreakPolicy.LineBreak` to preserve ordinary line breaks in
display and copy; the default is `Space`. HTML export accepts the same policy.

## Code and math styles

Code settings reuse codehigh's types and work with both Markdown and standalone `CodeBlock`.
Inline and block math share the native LaTeX configuration:

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

The default is `LatexConfig()` (20.sp, system light/dark, transparent background); Markdown colors
do not override it. LaTeX callbacks such as `onHyperlinkClick` belong in this configuration.
Keep the bundled KaTeX fonts for consistent glyphs and measurements. If you omit them, system fonts
are used and may have missing glyphs or layout differences:

```kotlin
implementation(libs.markdown.renderer) {
    exclude(group = "io.github.huarangmeng", module = "latex-fonts-katex")
}
```

## Directive plugins

Register custom Compose content through `directivePlugins`:

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

For external syntax such as `!VIDEO[...]`, add a `MarkdownInputTransformer` that produces standard
`{% tag ... %}` directives. See the [video plugin example](../markdown-preview/src/commonMain/kotlin/com/hrm/markdown/preview/DirectivePluginPreview.kt).
Transformers default to `MarkdownTransformerStreamingSupport.RestartOnRewrite`; declare `AppendSafe`
only if appended input always preserves the previous transformed output, or `Unsupported` for
full-content updates. `LocalMarkdownSourceMap` maps transformed positions back to original input.

Reuse plugin/transformer instances while their behavior stays the same. Replacing a transformer
refreshes content and source mappings. Renderer callbacks can change without resetting parsing.
The `Markdown(document = ...)` overload expects input transformations to have already run.

`MarkdownHtml.render(markdown = text, directivePlugins = plugins)` uses the same directive pipeline.
Provide `HtmlDirectiveFallback` / `HtmlInlineDirectiveFallback` for plugin-specific HTML output;
render scopes and HTML fallbacks expose the corresponding directive data.

## Parser transformations

Parser configurations are immutable: use `copy` for options and flavour `with` to add syntax or
processors while preserving the rest. Reuse configurations and extensions while behavior is unchanged.
For example, limit heading levels for both Compose and HTML:

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

A `BlockPostProcessor` returns the modified block, a replacement, or `null` to remove it.
Use `PostProcessor` for transformations that depend on the whole document.

## HTML behavior

Compose supports a constrained HTML subset with visible-source fallback. Attributes passed as
opaque data to callbacks do not become layout/CSS instructions or executable behavior.

| Element group | Supported behavior |
|---|---|
| `strong`/`b`, `em`/`i`, `del`/`s`/`strike`, `mark`, `sup`, `sub`, `ins`, `u`, `code`, `kbd` | Native text formatting; non-`on*` attributes retained as opaque data |
| `span` | Fully mapped `style`/`class`; other non-`on*` attributes retained as opaque data |
| `a`, `img`, `br` | `a`: safe `href` only; `img`: safe `src`, `alt`, `title`, `width`, `height`; `br`: no attributes |
| `p`, `div`, `center`, `article`, `section`, `main`, `header`, `footer` | Containers with `align` or restricted `style="text-align:..."` |
| `h1`–`h6`, `blockquote`, `hr` | Native blocks without attributes |
| `pre` / `code`, `ul` / `ol` / `li` | Safe code/list structures; code language via `language-...` / `lang-...` class; integer `ol start` |

Supported span CSS covers text/background colors, font weight/style, underline, and line-through.
Unknown span classes, unsupported CSS, `on*` event attributes, unsafe URL-bearing attributes,
and malformed structures cause source fallback. Unknown attributes on elements with strict
allowlists also cause fallback. Unsupported fragments, including tables, forms, and scripts, remain
visible as raw HTML rather than rendering partially.

Block tags must follow CommonMark block placement: put `<p>...</p>` on its own line, rather than
inside surrounding paragraph text. Inline and supported block comments are hidden; unsupported
block HTML stays raw.
Alignment accepts `left`, `start`, `center`, `right`, and `end`; `start`/`end` follow layout direction.

These rules apply to Compose display. `MarkdownHtml.render` passes raw HTML through; host applications
must apply their own policy when consuming exported HTML.
