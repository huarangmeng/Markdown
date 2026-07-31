plugins {
    id("org.jetbrains.kotlin.jvm")
    application
    alias(libs.plugins.kotlinxBenchmark)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":markdown-parser"))
    implementation(libs.kotlinx.benchmark.runtime)
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.hrm.markdown.benchmark.StreamingRenderBenchmarkKt")
}

benchmark {
    targets {
        register("main")
    }
    configurations {
        named("main") {
            warmups = 5
            iterations = 8
            iterationTime = 500
            iterationTimeUnit = "ms"
            outputTimeUnit = "us"
            reportFormat = "json"
            advanced("jvmForks", "2")
        }
        register("ci") {
            include("com.hrm.markdown.benchmark.ParserMicrobenchmark")
            warmups = 2
            iterations = 4
            iterationTime = 300
            iterationTimeUnit = "ms"
            outputTimeUnit = "us"
            reportFormat = "json"
            advanced("jvmForks", "1")
        }
    }
}

tasks.register<JavaExec>("inlineParseHeavyBenchmark") {
    group = "benchmark"
    description = "Runs the inline-parse-heavy benchmark (regress InlineParser hot path)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.InlineParseHeavyBenchmarkKt")
}

tasks.register<JavaExec>("incrementalEditBenchmark") {
    group = "benchmark"
    description = "Runs the incremental-edit benchmark (regress IncrementalEngine.applyEdit)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.IncrementalEditBenchmarkKt")
}

tasks.register<JavaExec>("llmStreamingBenchmark") {
    group = "benchmark"
    description = "Simulates LLM token-by-token streaming and reports per-append latency + frame-budget violations."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.LlmStreamingBenchmarkKt")
}

tasks.register<JavaExec>("llmRecomposeBenchmark") {
    group = "benchmark"
    description = "Simulates LLM streaming and counts equivalent Compose block recompositions via stableKey/contentHash diff."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.LlmRecomposeBenchmarkKt")
}

tasks.register<JavaExec>("mainThreadJankBenchmark") {
    group = "benchmark"
    description = "Measures whether running streaming append on the main thread causes frame jank, vs offloading to a background pool."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.MainThreadJankBenchmarkKt")
}

tasks.register<JavaExec>("coldStartBenchmark") {
    group = "benchmark"
    description = "Measures cold-start cost of HtmlEntities init, MarkdownParser construction, and first parse. Use --args='--isolate <stage>' for reliable numbers."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.ColdStartBenchmarkKt")
}

val performanceGateResults = layout.buildDirectory.file("reports/performance-gate/results.json")
val performanceGateBaseline = layout.projectDirectory.file("performance-baseline.json")
val performanceGateJmhJar =
    layout.buildDirectory.file("benchmarks/main/jars/markdown-benchmark-main-jmh-JMH.jar")

val runPerformanceGateBenchmarks by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs forked parser benchmarks with allocation and peak-heap profilers."
    dependsOn("mainBenchmarkJar", tasks.named("classes"))
    // kotlinx-benchmark's fat JAR contains dependencies and generated JMH classes, but not the
    // original benchmark class. Add the project's compiled output as a separate classpath entry;
    // this also makes the custom profiler visible to both the runner and its forked JVM.
    classpath = files(performanceGateJmhJar, sourceSets["main"].output)
    mainClass.set("org.openjdk.jmh.Main")
    args(
        "com.hrm.markdown.benchmark.ParserMicrobenchmark.*",
        "-wi", "5",
        "-i", "20",
        "-w", "500ms",
        "-r", "300ms",
        "-f", "1",
        "-tu", "us",
        "-bm", "avgt",
        "-prof", "gc",
        "-prof", "com.hrm.markdown.benchmark.PeakHeapProfiler",
        "-rf", "json",
        "-rff", performanceGateResults.get().asFile.absolutePath,
    )
    outputs.file(performanceGateResults)
    doFirst {
        outputs.files.singleFile.parentFile.mkdirs()
    }
}

tasks.register<JavaExec>("performanceGate") {
    group = "verification"
    description = "Fails when parser latency, throughput, allocation, or peak heap exceed baseline budgets."
    dependsOn(runPerformanceGateBenchmarks)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.PerformanceGateMainKt")
    args(
        performanceGateBaseline.asFile.absolutePath,
        performanceGateResults.get().asFile.absolutePath,
    )
    inputs.file(performanceGateBaseline)
    inputs.file(performanceGateResults)
}
