# Project: Markdown Parser (Kotlin Multiplatform)

## Key Files
- **BlockParser**: `markdown-parser/src/commonMain/kotlin/com/hrm/markdown/parser/block/BlockParser.kt` - main block parsing logic
- **LineCursor**: `markdown-parser/src/commonMain/kotlin/com/hrm/markdown/parser/core/LineCursor.kt` - line scanner with tab expansion
- **ListItemStarter**: `markdown-parser/src/commonMain/kotlin/com/hrm/markdown/parser/block/starters/ListItemStarter.kt`
- **BlockQuoteStarter**: `markdown-parser/src/commonMain/kotlin/com/hrm/markdown/parser/block/starters/BlockQuoteStarter.kt`
- **Spec test**: `markdown-parser/src/jvmTest/kotlin/com/hrm/markdown/parser/CommonMarkSpecTest.kt`
- **Spec data**: `markdown-parser/src/jvmTest/resources/commonmark-spec.txt`

## Testing
- CommonMark spec: `./gradlew :markdown-parser:jvmTest --tests "com.hrm.markdown.parser.CommonMarkSpecTest" --rerun`
- Results written to `/tmp/commonmark-results.txt`
- Current status: **652/652 (100%)**
- 3 pre-existing failures in other test classes (BlockAttributeTest, InlineParserTest, UrlPercentEncodingTest)

## Architecture Notes
- `LineCursor` handles partial tab consumption via `partialTabSpaces` field
- `Snapshot` includes partialTabSpaces for save/restore
- `rest()` prepends virtual spaces from partial tabs
- Lazy continuation works through BlockQuote, ListBlock, and ListItem containers
- `wouldStartNonLazyBlock` respects 0-3 space indent limit (4+ is indented code, can't interrupt)
- `isListTight` checks: containsBlankLine flag, gaps between direct children, trailing blanks within items, gaps between consecutive items
- `finalizeBlock` for ListBlock/ListItem computes endLine from children for accurate lineRanges
- Fenced code literal: closed fences use per-line newline join; unclosed fences use separator join

## Conventions
- Comments in codebase are in Chinese
- User prefers lowercase english comments in new code
- No Co-Authored-By in commits
