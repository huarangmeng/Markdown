# Benchmark module guidance

Follow the repository [AGENTS.md](../AGENTS.md). This module owns parser and renderer workloads, performance
regression checks and budget maintenance. Publish user-facing verified results in the root READMEs.
See [BENCHMARKS.md](BENCHMARKS.md) for scenarios, report formats and revision comparisons.

## Common verification

Run from the repository root, one benchmark process at a time:

```bash
./gradlew :markdown-benchmark:performanceGate
./gradlew :markdown-benchmark:fullParsingBenchmark
./gradlew :markdown-benchmark:incrementalScalingBenchmark --args='--warmups 20 --iterations 40 --coalesce 16'
./gradlew :markdown-benchmark:failedInlineScalingBenchmark
./gradlew -q :markdown-benchmark:growingBlockStreamingBenchmark --args='--revision CANDIDATE_SHA'
./gradlew -q :markdown-benchmark:inlineFlowLayoutBenchmark -PbenchmarkRevision=CANDIDATE_SHA
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

## Measurement rules

- The CI gate uses forked JMH AverageTime. `iterationMeanP95` is the p95 of iteration means,
  not individual-call tail latency; baseline keys `p95Us` / `maxP95Us` retain their legacy names.
- Throughput is derived from average time. `gc.alloc.rate.norm` measures bytes/op; observed heap-pool
  peak delta is neither retained AST size nor process RSS.
- Keep lazy parsing and fully materialized AST measurements distinct. Input construction and parser
  setup exclusions depend on the suite; document them when reporting results.
- Separate buffered receives, actual parse commits, consumption and finalization. Streaming gate
  batches use 16-character coalescing, newline commits and exactly one `endStream()`.
- Compare identical workloads, harness, JDK, machine, heap, power mode and arguments. Keep raw reports
  and revision metadata; observational timing or a passing stress test does not prove complexity.

## Budgets and execution

Do not relax [performance-baseline.json](performance-baseline.json) to make an optimization pass.
Before justified budget maintenance, collect at least three same-environment reports matching CI's
JDK, OS image, CPU class and power mode, inspect profiles/traces, and explain the affected budget.
A single noisy run is insufficient; preserve tolerance for shared-runner variance.

Run benchmark tasks serially. Gradle orders gate, full-parse, failed-inline, SampleTime and growing-block
suites when requested together; separate invocations must not overlap. Do not disable JMH's global
lock or run competing benchmark processes on the same machine.

The gate report is `build/reports/performance-gate/results.json`; other outputs are listed in
[BENCHMARKS.md](BENCHMARKS.md). New observational suites do not silently redefine gate workloads.

## Renderer layout

`inlineFlowLayoutBenchmark` uses a separate `rendererBenchmark` source set and a Kotlin friend path
to the real renderer JVM artifact. Keep its Compose/Skia dependencies off the parser/JMH classpath.
The task selects the host desktop runtime and prints revision, JVM, OS, configuration and CSV rows.
Run with `--args='8000'` to cap input length. This observational suite has no parser gate budget.
Its fixed formula/component placeholders measure text-flow layout, not live LaTeX or UI painting;
real formula/plugin layout and activation are covered by renderer common tests. Deterministic
measurement-work budgets also live in renderer common tests; wall-clock thresholds do not.

## Android and related checks

Android Macrobenchmark requires a physical device and a stable device pool for comparisons.
Emulators provide functional smoke checks, not performance baselines. Reports and Perfetto traces:
`macrobenchmark/build/outputs/connected_android_test_additional_output/`.

Parser correctness, deterministic scan/allocation guards and stress budgets belong in the
[parser guidance](../markdown-parser/AGENTS.md); algorithm limits are in
[INCREMENTAL_PARSING.md](../markdown-parser/INCREMENTAL_PARSING.md).
