# Performance regression checks

This module has two complementary benchmark layers.

## Parser gate

Run the same forked JMH workload used by pull-request CI:

```bash
./gradlew :markdown-benchmark:performanceGate
```

The task runs `ParserMicrobenchmark` in a fresh JVM fork, records a JSON report at
`markdown-benchmark/build/reports/performance-gate/results.json`, and fails when any scenario
exceeds the checked-in budgets in `performance-baseline.json`.

The gate checks:

- primary-score p95 latency;
- throughput derived from average time;
- `gc.alloc.rate.norm` bytes allocated per operation;
- the additional peak heap observed during an iteration.

The streaming scenario includes `endStream()` exactly once per measured operation. Longer local
runs are available through `mainBenchmark`; `ciBenchmark` is a shorter smoke configuration.

### Updating budgets

Do not update the baseline from a single noisy run. Use the same JDK, OS image, CPU class, and power
mode as CI; collect at least three reports; compare profiles/traces; then change only the affected
budget. Keep enough tolerance for shared-runner variance, and explain the intentional regression or
optimization in the pull request.

## Compose Macrobenchmark

The `macrobenchmark` module launches a release-like, profileable benchmark Activity and measures the
real parser/compile/layout/Compose pipeline. It includes cold first display, repeated long-document
flings, and a long-press drag that selects across visible Markdown blocks.

Run it on a physical Android device:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Macrobenchmark writes frame/startup metrics, JSON output, and Perfetto traces under
`macrobenchmark/build/outputs/connected_android_test_additional_output/`. Compare results only on a
stable device pool; emulator numbers are suitable for functional smoke checks, not performance
baselines.

## Inline cache telemetry

The renderer's two inline caches report hit, miss, eviction, entry-count, and estimated-byte
snapshots through `InlineLayoutMetricsSnapshot`. Both caches are bounded by entry count and estimated
retained bytes, and the common tests exercise width/font-scale churn to prevent an unbounded resize
working set.
