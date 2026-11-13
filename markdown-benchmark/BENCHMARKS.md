# Benchmark scenarios and comparisons

Execution and budget rules are in [AGENTS.md](AGENTS.md). Paths below are relative to this module.

## Suites

| Task | Workload and measurement | Output |
|---|---|---|
| `performanceGate` | Historical mixed full parse, lazy inline-heavy parse, complete streaming batch; fresh parser per operation | `build/reports/performance-gate/results.json` |
| `fullParsingBenchmark` | 3,000-line fence, 3,000 definitions, long multiline paragraph, materialized inline AST, 1,500-row GFM table with lazy cell inlines | `build/reports/full-parsing/results.json` |
| `incrementalScalingBenchmark` | Complete tail append / middle equal-length replace at 100 / 1,000 / 10,000 paragraphs, CommonMark and Extended | stdout |
| `failedInlineScalingBenchmark` | Fully materialized failed scans at 1,024 / 4,096 / 16,384 characters | `build/reports/failed-inline-scaling/results.json` |
| `parserCallSampleTimeBenchmark` | Closed-loop individual-call latency at fixed 100 / 1,000 / 10,000 paragraphs; lazy / materialized existing AST | `build/reports/parser-call-sample-time/results.json` |
| `growingBlockStreamingBenchmark` | Repeated small appends to one growing block, with optional consumption after each commit | stdout CSV plus `#` metadata |
| `inlineFlowLayoutBenchmark` | Actual renderer text flow at 1,000–32,000 characters, with and without inline placeholders | stdout CSV plus `#` metadata |
| `:markdown-renderer:renderCompilerScalingBenchmark` | Parser commits and real render-model compilation at 100 / 1,000 / 10,000 stable paragraphs | stdout CSV plus `#` metadata |

JMH AverageTime percentiles describe iteration means. The scaling harnesses instead report empirical
individual-call samples; their setup and sample counts differ, so do not compare those percentiles
directly. The dedicated SampleTime suite below also samples individual calls. `mainBenchmark`
provides the broader JMH configuration; `ciBenchmark` is a smoke run.

Full-parse suites construct input outside measurement. Incremental scaling also excludes parser
setup, full-source String materialization and whole-AST traversal. Its `--coalesce 16` append reaches
the threshold and commits real parsing work. Allocation growth guards run separately in parser tests.

## Long paragraph layout

```bash
./gradlew -q :markdown-benchmark:inlineFlowLayoutBenchmark -PbenchmarkRevision=CANDIDATE_SHA
# Cap the input size for baseline comparisons:
./gradlew -q :markdown-benchmark:inlineFlowLayoutBenchmark --args='8000' -PbenchmarkRevision=BASELINE_SHA
```

This opt-in suite calls the actual inline flow engine through a separate `rendererBenchmark` source
set. Kotlin friend access avoids exposing internal renderer APIs. Renderer/Compose/Skia dependencies
do not enter the historical parser/JMH source set or performance gate.

The fixed-width workload uses a 260 px line, 16 sp text, 24 sp line height, density/font scale 1,
and 1,000 / 2,000 / 4,000 / 8,000 / 16,000 / 32,000 characters. Five 1,000-character text layouts
warm up each fresh JVM; each scenario/size records three samples and reports their median. Heap is
256/512 MiB. Input, font resolver setup, parsing and painting are excluded; each timed operation
includes a fresh `TextMeasurer` and uncached flow layout. `mixed_placeholders` inserts fixed 96×36 px
formula and 40×24 px component specs. It does not benchmark live LaTeX measurement or UI rendering.
Real LaTeX, inline code, custom directive activation, narrow/fractional widths and font scaling are
tested separately in renderer common tests.

CSV rows include line count, measurement calls and measured UTF-16 characters. Counters cover
transient text/window measurements; the fixed `Ag` baseline measurement is excluded. The renderer's
normal call sites leave these optional counters disabled. Deterministic tests require bounded work
and prevent remeasurement of the full remaining tail on each line; wall-clock samples remain
observational and do not redefine parser budgets. Preserve revision/JVM/OS/configuration metadata
and follow the serial, same-environment comparison rules in [AGENTS.md](AGENTS.md).

## Failed inline scans

Shapes are `unmatched-closers` (`a* `), `url-punctuation` (`https://a` plus a long dot suffix),
`directive`, `spoiler`, `unequal-backticks` and `wiki`. A text prefix prevents block-level interception;
each fresh-parser operation forces all inline containers. Input construction is excluded.

The suite uses two JVM forks, three 250 ms warmups, five 250 ms measurements and a 256/512 MiB
initial/maximum heap. Restrict parameters without editing the workload:

```bash
./gradlew :markdown-benchmark:failedInlineScalingBenchmark -PfailedInlineShapes=wiki,directive -PfailedInlineChars=1024,4096,16384
```

## Growing single-block streams

Defaults: three warmups, five measured streams, 1,024 / 4,096 / 16,384 ASCII characters, eight-character
chunks, 16-character coalescing, CommonMark / Extended, and both materialization modes. Heap is fixed
at 256/512 MiB. The stream starts empty; there is no large initial append or simulated arrival delay.

| Option | Values / meaning |
|---|---|
| `--sizes` | Comma-separated character counts; ASCII bytes equal UTF-16 units |
| `--chunk`, `--coalesce` | Positive chunk size; nonnegative parser coalescing threshold |
| `--shapes` | Default: `single-line,multiline-paragraph,open-fence`; optional: `long-list,long-table` |
| `--flavours` | Default: `commonmark,extended`; optional: `gfm` |
| `--materialize` | `none,commit` |
| `--warmups`, `--iterations`, `--revision` | Sampling configuration and recorded parser revision |

`none` leaves inline ASTs and deferred code literals lazy. `commit` traverses the tree and reads every
leaf literal after each publication and finalization; requesting the complete growing String has its
own output cost. Source/chunk construction, parser setup, sample arrays and final equality checks are
outside measurement. The complete `batch` envelope includes counter collection and bookkeeping.

CSV dimensions identify shape, flavour, size, chunk, threshold, materialization and operation. Rows
separate `buffer-only`, `commit`, `consume-commit`, `end-stream`, `consume-end` and `batch`. Nonempty
ASCII appends returning the previous root are buffered; committed updates publish a new root.
Metrics include sample count, p50/p95/p99/max, mean allocated bytes and cumulative time/allocation.
Totals span measured iterations; unsupported thread-allocation counters print `NA`. Batch percentiles
have only one sample per stream and are descriptive with small sample counts. Preserve the `#`
revision/JVM/OS/configuration metadata when saving stdout; strip it only for CSV import.

To inspect larger inputs, add `--sizes 4096,16384,65536` to the command in [AGENTS.md](AGENTS.md).
Keep small chunks when checking cumulative complexity. Parser JVM tests separately require less than
14-fold allocation growth for 512 versus 4,096 single-line / open-fence appends without reading each
published literal; final content checks occur outside the allocation counter.

Lists and tables deliberately keep one container growing, and can be expensive. They validate the
final AST's actual list/table type; an unsupported flavour cannot silently benchmark a paragraph.
Start with explicit small inputs; the default shape/flavour matrix remains unchanged:

```bash
./gradlew -q :markdown-benchmark:growingBlockStreamingBenchmark --args='--shapes long-list,long-table --flavours gfm,extended --sizes 1024,4096 --revision CANDIDATE_SHA'
```

## Individual parser-call SampleTime

```bash
./gradlew :markdown-benchmark:parserCallSampleTimeBenchmark
# Restrict a smoke run without changing the measured method:
./gradlew :markdown-benchmark:parserCallSampleTimeBenchmark -PsampleTimeParagraphs=100 -PsampleTimeOperations=append-commit -PsampleTimeExistingAst=lazy
```

This separate native JMH `SampleTime` workload reports **closed-loop sampled parser-call p95/p99**.
In its JSON, `mode` is `sample`, and `primaryMetric.scorePercentiles["95.0"]` / `["99.0"]` describe
sampled individual benchmark invocations (batch size one) in microseconds, rather than the historical gate's
AverageTime iteration means. Keep sample counts, histograms and run-to-run variation with the result;
a short run's rare percentiles cannot establish a production tail-latency guarantee.

Parameters are `paragraphs=100,1000,10000`, `operation=tail-replace,middle-replace,append-commit` and
`existingAst=lazy,materialized`. CommonMark is fixed. Eighteen cases use two JVM forks, one worker,
two 250 ms warmups, four 500 ms measurements and a 256/512 MiB heap: 90 seconds of scheduled warmup
and measurement, plus setup/fork overhead. The Gradle properties in the command restrict parameters.

Trial setup builds and parses the source outside timing. Replacements alternate `X` / `Y` at a
fixed tail or middle offset, ensuring real equal-length updates with constant size. Each streaming
append is exactly 16 characters with a 16-character threshold, so it commits; Invocation teardown
deletes that chunk to restore the original size. Lightweight fixture checks verify committed content
and source length without flattening the document. This is a stable-size append-call experiment;
use the growing-stream suite for accumulated output costs.

`materialized` prepares the complete existing AST in Trial setup and the changed/restored paragraph
in Invocation teardown; `lazy` leaves them unmaterialized. The sampled method calls only the parser
API, with a constant operation dispatch. Initial construction, restoration and AST consumption are
outside its sampled interval. Fixtures can still cause GC/cache effects, alter call spacing and add
timer/harness overhead. Pauses during restoration are omitted (coordinated omission), so these are
neither arrival-driven service tails nor UI/frame latency. The task intentionally omits the GC
profiler: fixture-inclusive bytes/op would not isolate parser-call allocation. Existing allocation
guards and the AverageTime gate remain separate and unchanged. This task joins the existing serial
benchmark ordering when requested with the other suites.

## Comparing parser revisions

1. Prepare clean baseline and candidate checkouts with identical benchmark sources/build script,
   baseline JSON, dependencies and JDK. Copy the harness into an older checkout if absent; exclude
   `build/`. Record both parser revisions and the harness revision or patch.
2. Follow the same-environment and serial-execution rules in [AGENTS.md](AGENTS.md). Alternate revision
   order and collect at least three fresh-process runs each. Save JSON before it is overwritten;
   redirect each quiet growing-stream run to a separate CSV with its `--revision` label.
3. Compare matching configuration rows across all runs, including allocation, consumption and batch
   totals. Fourfold input with roughly fourfold cost suggests linear growth; roughly sixteenfold
   suggests quadratic growth on that input family, not a proof of worst-case complexity.
4. Recheck the historical gate, full-parse and incremental scaling suites to detect transferred costs.
   Keep correctness and deterministic growth checks separate from observational timings.

## Rendering checks

```bash
./gradlew -q :markdown-renderer:renderCompilerScalingBenchmark --args='--revision CANDIDATE_SHA'
```

This compiler harness separates `parser-commit` and real compiler cost. `--compiler cold,incremental`
(the default) compares full `DefaultRenderModelCompiler` reconstruction with a publication-driven
`RenderCompileSession`. Incremental rows include actual visited-node and compiled-root-block counts.
Defaults use already displayed inlines; `--initial displayed,cold` also isolates
the first materialization in a `compile-first-inline-materialization` row. Input/setup, initial display,
endStream, verification and IR consumption are excluded; stage time and thread allocation go to CSV.
It does not measure layout or Compose. Preserve its revision/environment metadata when saving stdout.
`--initial cold` means unmaterialized prefix inlines; `--compiler cold` means complete model
reconstruction on every update (including displayed inlines). Neither means process cold start.
The default 40 warm samples (five first-materialization samples) provide observational percentiles;
fixed parameter order and short warmups can retain JIT/GC bias, so repeat fresh processes before comparison.
Stages share a heap: compiler allocation can trigger a pause in a subsequent parser sample.

Android Macrobenchmark measures real parser/compile/layout/Compose work: cold display, long-document
flings and cross-block selection. Its physical-device requirement and output path are in
[AGENTS.md](AGENTS.md). `InlineLayoutMetricsSnapshot` exposes renderer cache hits, misses, evictions,
entry counts and estimated bytes; common tests cover width/font-scale churn and bounded retention.

## Same-machine observations

Revision label `7972986-plus-verification`, JDK 21.0.10, macOS/aarch64, one process per CLI and five
measured trials. Compiler times are medians; MB means decimal mean allocated bytes per compile.

| Scenario | Observed change | Context |
|---|---|---|
| Growing list, 1,024 → 4,096 characters | Whole-stream allocation 10.6–14.5× | GFM / Extended; lazy / consumed snapshots |
| Growing table, 1,024 → 4,096 characters | Whole-stream allocation 12.8–14.2× | Same variants; batch includes consumption/bookkeeping |
| Displayed prefix, 100 → 10,000 paragraphs | Compiler 0.793 → 53.186 ms; 0.503 → 34.929 MB | Parser commit 0.090 → 0.117 ms |

Raw files: `build/reports/growing-block-streaming/structured.csv` and
`../markdown-renderer/build/reports/render-compiler-scaling/results.csv`. These are single-process
observations, not new budgets or worst-case proofs. They expose growing-container and whole-document
compiler costs; they do not measure layout/Compose. The historical baseline remains unchanged.

Publication-driven compilation now consumes verified versioned splices; published ASTs reject external
writes and IR collections are isolated read-only values. Session tests require cold-compile equivalence,
stable-prefix IR reuse and bounded visited/compiled nodes; the JVM allocation guard checks a 100-fold
stable-prefix increase. Skipped versions, environment/global changes rebuild; stale AST views are rejected.
Container-level list/table checkpoints remain future work; faster compilation alone does not establish
end-to-end UI performance.

### Publication candidate verification

Revision label `2786347-plus-publication-final`, JDK 21.0.10, macOS/aarch64. Three fresh processes
alternate compiler order (`cold,incremental`, `incremental,cold`, `cold,incremental`), with
`--initial displayed,cold`; each displayed case contains 40 measured samples. For a displayed
10,000-paragraph prefix and a two-character tail update, the observed per-process ranges are:

| Compiler | Median time | Mean allocation per compile | Incremental work |
|---|---|---|---|
| Full reconstruction | 52.221–53.869 ms | 39.983 MB | Complete document |
| Publication splice | 6.083–7.584 µs | 5.699–5.715 kB | 6 visited nodes, 1 compiled root block |

Raw CSVs are `../markdown-renderer/build/reports/render-compiler-scaling/publication-{1,2,3}.csv`.
These are compiler-stage observations for a fixed small update, not UI latency or guarantees for
arbitrary edits. Short warmups, a shared heap and GC/JIT effects still limit timing conclusions.
The separate JVM allocation guard observes 3,680 / 3,928 / 3,768 bytes for 100 / 1,000 / 10,000 stable
paragraphs, always visiting three nodes; another guard checks slot retention after repeated deletions.

Publication isolation has a parser cost. The unchanged historical gate passes, but measured allocation
increases relative to the preceding `2786347` verification: full parse 2.906 → 3.203 MB/op (+10.2%),
inline-heavy 0.573 → 0.617 MB/op (+7.6%), streaming batch 19.472 → 23.279 MB/op (+19.6%). Average
times in those two runs are respectively 1,139.81 → 1,228.44 µs, 157.31 → 219.38 µs, and
9,794.90 → 7,222.19 µs. The earlier streaming run contains a large outlier; this single gate pair
does not establish a timing improvement. Keep the parser overhead visible alongside the compiler gain.
Layout metadata and selection indexes still traverse all blocks; this change does not remove those costs.

Final verification: 1,070 JVM tests and eight isolated stress tests pass without skips; parser and
renderer common tests compile for JS/Wasm, and parser sources compile for Android and both iOS arm64
targets. These platform compilations are not browser or device test execution. The full-parse JMH suite
also completes; no performance budget is relaxed.

### Publication allocation follow-up

Baseline `4125a99` versus `4125a99-plus-allocation-final`, same JDK 21.0.10 and macOS/aarch64
machine. Frozen parser artifacts use the same harness and gate arguments: five 500 ms warmups,
twenty 300 ms measurements, one fork, AverageTime, GC allocation and peak-heap profilers.
Three fresh processes per revision run serially in baseline/candidate, candidate/baseline,
baseline/candidate order. Per-process allocation ranges use decimal MB/op; reductions compare
the corresponding process pairs, rather than unrelated range endpoints.

| Scenario | Baseline MB/op | Candidate MB/op | Paired allocation reduction |
|---|---|---|---|
| Full parse | 3.188–3.203 | 2.919–2.950 | 7.5%–8.6% |
| Inline-heavy, lazy inlines | 0.6171–0.6172 | 0.5589–0.5750 | 6.8%–9.4% |
| Streaming batch, coalesce 16 | 23.137–23.289 | 19.612–19.675 | 15.0%–15.8% |

All six reports pass the unchanged historical budgets. Average-time ranges are respectively
1,230.66–1,254.04 → 1,167.14–1,207.49 µs, 166.98–227.70 → 154.72–208.29 µs, and
7,302.37–7,382.53 → 6,761.23–7,315.32 µs. Timing does not improve in every pair: the second
inline-heavy pair is 166.98 → 208.29 µs. These short-run JIT/GC states support a repeatable
allocation reduction, not a general latency improvement claim.

The changes delay unused container/map storage and views, document authority and diagnostics;
internal publication traverses changed regions directly. Copies reuse only owned immutable
snapshots, retaining defensive copies for live views and mutable inputs. Public APIs are unchanged.
Raw gate JSONs and artifact metadata are under `build/reports/publication-allocation/`.

The JVM fixed-work allocation tests add ceilings for the exact fixtures: 1,000 lazy paragraphs
including parser construction ≤2.75 MB, and 128 actual 16-character tail commits ≤2.20 MB for
both lazy and displayed prefixes. Input/setup, initial display, final consumption and endStream
are excluded. Every commit's revision and source length are verified. Final JVM observations are
2,249,528 B and 1,981,560 B respectively; ceilings allow JIT variation and are not portable
per-document limits. Existing growth checks and historical budgets are unchanged.

Consumption is checked separately: the two-fork full-parse suite observes fully materialized inline
allocation 1.833 → 1.619 MB/op (−11.7%) and table metadata 4.609 → 3.958 MB/op (−14.1%). Long code,
long paragraphs and consecutive definitions remain slightly lower in allocation (0.02%–0.11%).
These are one suite report per revision, not three paired timing comparisons. The real compiler
follow-up still visits six nodes and compiles one root for a displayed 10,000-paragraph prefix;
one process observes 6.042 µs median and 5,523 B/compile. Layout/Compose remain outside this measure.

The final Gradle gate and full-parse suite both pass. Final verification includes 1,083 JVM tests,
eight isolated stress checks, parser/renderer JS and Wasm test compilation, and parser Android/iOS
arm64 compilation. No device or browser execution is implied. Corresponding reports, including
`candidate-full.json`, `candidate-official-gate.json` and `candidate-compiler.csv`, are retained in
`build/reports/publication-allocation/`.

### Immutable configuration and cache dependency refactor

Baseline `4f39550` versus `4f39550-plus-api-refactor`, using the same JDK, machine, default JVM
ergonomics and gate arguments as above. Three fresh processes per revision run serially in
baseline/candidate, candidate/baseline, baseline/candidate order. Frozen baseline artifacts are
isolated from the candidate build. Generated JMH harnesses, profilers and gate checks match;
constructor calls are mechanically migrated to the new configuration API in the same measurement
positions. Results include public API construction costs, rather than isolating the core algorithm.

| Scenario | Baseline MB/op range | Candidate MB/op range | Paired allocation change |
|---|---|---|---|
| Full parse | 2.929–2.942 | 2.920–2.948 | −0.51% to +0.65% |
| Inline-heavy, lazy inlines | 0.5685–0.5750 | 0.5736–0.5737 | −0.24% to +0.90% |
| Streaming batch, coalesce 16 | 19.648–19.696 | 19.562–19.615 | −0.58% to −0.17% |

All six reports pass the unchanged historical budgets. Allocation stays within 1% of the preceding
implementation in these fixtures. Average-time ranges are 1,101.17–1,164.85 → 1,103.49–1,226.53 µs,
151.77–202.84 → 193.28–214.63 µs, and 6,378.88–6,564.80 → 6,378.31–6,553.87 µs. Individual pairs
move in both directions; these observations do not establish a general latency improvement.
No workload, coalescing policy or performance budget is weakened.

Fixed-work JVM allocation checks observe 2,249,392 B for 1,000 lazy paragraphs and 1,979,512 B for
128 actual tail commits, for both lazy and displayed prefixes. The existing 2.75 MB / 2.20 MB
ceilings and measurement exclusions remain unchanged. Final functional verification passes 1,106
JVM tests and eight isolated stress checks without failures or skips. Parser/renderer tests compile
for JS/Wasm; parser sources compile for Android and both iOS arm64 targets. These are compilation
checks, not browser or device execution. Raw reports and artifact metadata are retained under
`build/reports/parser-refactor/`.

The final official Gradle gate and five-shape, two-fork full-parse suite also complete successfully.
The real compiler still visits six nodes and compiles one root for a displayed 10,000-paragraph
prefix: one process observes 6.125 µs median and 5,523 B per warm compile. First inline materialization
and cold compilation remain separate rows. This compiler measure excludes layout and Compose.
