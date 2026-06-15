# AGENTS.md

Guidance for Codex when working in this repository.

## What This Repo Is

Kotlin Multiplatform Markdown engine for Compose Multiplatform.

- Platforms: Android, iOS, Desktop (JVM), Web (Wasm/JS)
- Stack: Kotlin `2.3.10`, Compose Multiplatform `1.10.1`

## Read This First

- `markdown-parser`: syntax -> AST, no UI deps
- `markdown-runtime`: directive plugins, input transforms, runtime pipeline
- `markdown-renderer`: AST -> Compose / HTML
- `markdown-preview`: demo data and preview categories
- `composeApp`: thin shell around preview UI

Dependency chain:

```text
markdown-parser
      ↑
markdown-runtime
      ↑
markdown-renderer
      ↑
markdown-preview
      ↑
composeApp
```

## Module Notes

### `markdown-parser`

- Entry: `MarkdownParser`
- Owns block parsing, inline parsing, flavours, incremental parsing, diagnostics
- Keep it UI-free
- Prefer new syntax via `BlockStarter`, `PostProcessor`, or flavour config

### `markdown-runtime`

- Entry points: `MarkdownDirectivePlugin`, `MarkdownDirectiveRegistry`, `MarkdownDirectivePipeline`
- Owns directive input normalization and plugin registration
- Use this layer for external syntax -> directive transforms
- Do not move Compose rendering concerns into this module

### `markdown-renderer`

- Entry: `Markdown()` and `MarkdownHtml`
- Owns block renderers, inline rendering, theme, image loading, HTML export
- Compose-specific changes belong here, not in parser/runtime

### `markdown-preview`

- Owns `PreviewCategory` / `PreviewGroup` / `PreviewItem`
- Add demos here when new renderer or syntax support is added

## Commands

```bash
# Focused tests
./gradlew :markdown-parser:jvmTest
./gradlew :markdown-runtime:jvmTest
./gradlew :markdown-renderer:jvmTest

# Full JVM verification
./gradlew jvmTest

# Demo app
./gradlew :composeApp:run
```

All tests must pass before commit.

## Working Rules

- Parser changes:
  - Prefer `BlockStarter` / `PostProcessor`
  - Avoid patching `BlockParser` / `InlineParser` directly unless necessary
- Runtime changes:
  - Put directive/plugin coordination in `markdown-runtime`
  - Keep public API naming consistent with `Directive`
- Renderer changes:
  - Keep `InlineRenderer` and `InlineFlowText` split by responsibility
  - For placeholder-based inline content, prefer a single source of truth
- Preview changes:
  - Add or update demo cases in `markdown-preview`
- Docs changes:
  - Update `README.md`, `README_zh.md`, and `markdown-parser/PARSER_COVERAGE_ANALYSIS.md` when behavior changes

## Common Workflows

### Add New Syntax

1. Add parser support in `markdown-parser`
2. Add parser tests
3. Add renderer support in `markdown-renderer`
4. Add renderer tests if needed
5. Add preview/demo
6. Update coverage/doc files
7. Run `./gradlew jvmTest`

### Add New Directive Plugin Capability

1. Extend `markdown-runtime`
2. Thread runtime API into `markdown-renderer`
3. Add preview/demo using `directivePlugins`
4. Update README usage examples
5. Run focused tests, then `./gradlew jvmTest`

## Conventions

- Packages:
  - `com.hrm.markdown.parser`
  - `com.hrm.markdown.runtime`
  - `com.hrm.markdown.renderer`
  - `com.hrm.markdown.preview`
- AST nodes are sealed; `ContainerNode` supports lazy inline parsing
- `stableKey` is line-based and used for Compose stability
- `contentHash` is for incremental reuse, not UI identity
- Test naming: `should_expectedBehavior_when_condition`
