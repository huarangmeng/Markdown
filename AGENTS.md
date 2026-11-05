# AGENTS.md

Guidance for Codex when working in this repository.

## What This Repo Is

Kotlin Multiplatform Markdown engine for Compose Multiplatform.

- Platforms: Android, iOS, Desktop (JVM), Web (Wasm/JS), experimental macOS (Kotlin/Native, arm64)
- macOS renderer support requires macosArm64 publications of LaTeX (including fonts-katex), codehigh and diagram. Do not replace missing publications with JVM or iOS artifacts.
- Stack: Kotlin and Compose Multiplatform; use `gradle/libs.versions.toml` as the version source of truth

## Read This First

- `markdown-parser`: syntax -> AST, no UI deps
- `markdown-runtime`: directive plugins, input transforms, runtime pipeline
- `markdown-renderer`: AST -> Compose / HTML
- `markdown-preview`: demo data and preview categories
- `composeApp`: thin shell around preview UI
- `androidapp`: Android demo app
- `markdown-benchmark`: parser JMH workloads, allocation/scaling checks, performance budgets
- `macrobenchmark`: Android startup, scrolling, and cross-block selection measurements

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

The preview and demo modules are repository clients; applications using the published renderer do not need them.
Parser-specific invariants and verification live in [markdown-parser/AGENTS.md](markdown-parser/AGENTS.md).
Benchmark commands and baseline maintenance live in [markdown-benchmark/AGENTS.md](markdown-benchmark/AGENTS.md).

## Module Notes

### `markdown-parser`

- Entry: `MarkdownParser`; parser, Compose and HTML share `com.hrm.markdown.parser.MarkdownParserConfig`
- Owns block parsing, inline parsing, flavours, incremental parsing, diagnostics
- Keep it UI-free
- Prefer new syntax via `BlockStarter`, `BlockPostProcessor`, `PostProcessor`, or immutable flavour config

### `markdown-runtime`

- Entry points: `MarkdownDirectivePlugin`, `MarkdownDirectiveRegistry`, `MarkdownDirectivePipeline`
- Owns directive input normalization and plugin registration
- Use this layer for external syntax -> directive transforms
- Do not move Compose rendering concerns into this module

### `markdown-renderer`

- Entry: `Markdown()` and `MarkdownHtml`; public settings use `MarkdownColors`, `MarkdownTypography`, `MarkdownLayout`, `MarkdownParserConfig`, `MarkdownState`, `MarkdownComponents`, and `MarkdownInteractions`
- Owns block renderers, inline rendering, theme, image loading, HTML export
- Compose-specific changes belong here, not in parser/runtime
- `Markdown(document = ...)` consumes current parser publications. Flavour transformations finish
  before publication; published ASTs reject external writes. Render compilation applies verified
  publication splices and retains immutable IR, with identities independent of source coordinates.
- Input-transformer identity controls parser state; renderer callbacks control layout/rendering.
  IR compilation depends only on heading numbering and soft-break policy, not streaming state.

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

# Compile common parser/renderer tests for Web (compilation only, not browser execution)
./gradlew :markdown-parser:compileTestKotlinJs :markdown-parser:compileTestKotlinWasmJs :markdown-renderer:compileTestKotlinJs :markdown-renderer:compileTestKotlinWasmJs

# Extreme inputs in bounded child JVMs; separate from jvmTest
./gradlew :markdown-parser:stressTest

# Parser performance budgets
./gradlew :markdown-benchmark:performanceGate

# Demo app
./gradlew :composeApp:run
```

All tests must pass before commit.

For macOS Native changes, run `:markdown-parser:macosArm64Test`, `:markdown-runtime:macosArm64Test` and `:markdown-renderer:macosArm64Test` on an arm64 Mac, with `org.jetbrains.compose.experimental.macos.enabled=true`. Windows metadata compilation is not native linking or test execution. The macOS CI job verifies both native tests and published variant metadata; missing transitive macOS publications must be fixed upstream before renderer support can be delivered.

CI is defined in [test.yml](.github/workflows/test.yml): `JVM Tests` runs regular JVM tests and Web test
compilation; `Parser Stress Tests` runs isolated extreme-input checks; `JVM Performance Gate` checks
parser performance budgets. Each job uploads its reports on success or failure. Android Macrobenchmark
requires a physical device and is documented in the benchmark module's AGENTS.md.

## Documentation Audiences

- `README.md` and `README_zh.md` serve component users: supported features, installation, public APIs,
  examples, observable behavior, compatibility, and verified results. Keep the two languages aligned.
- Put repository architecture, module boundaries, implementation details, test commands, verification
  procedures, resource budgets, and baseline maintenance in the root or owning module's `AGENTS.md`.
- Keep root guidance cross-module; put module-specific instructions in that module and link them here.
  Detailed design records may stay beside their module, linked from its AGENTS.md.
- README quality claims must describe what was actually checked. Keep test execution separate from
  test compilation, and do not present a functional checklist as measured code coverage.

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
  - Update both READMEs when user-facing behavior or public APIs change
  - Update `markdown-parser/PARSER_COVERAGE_ANALYSIS.md` when parser syntax support changes
  - Update the owning `AGENTS.md` when architecture or verification workflows change

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
- Published blocks use `persistentId` for render identity; custom nodes without positions use tree paths
- `contentHash` is for incremental reuse, not UI identity
- Test naming: `should_expectedBehavior_when_condition`
