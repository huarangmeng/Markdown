import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

// Keep renderer/Skia dependencies out of the historical parser/JMH classpath.
val rendererBenchmark = sourceSets.create("rendererBenchmark")
val desktopOs = when {
    System.getProperty("os.name").startsWith("Mac") -> "macos"
    System.getProperty("os.name").startsWith("Windows") -> "windows"
    else -> "linux"
}
val desktopArch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "arm64" else "x64"
dependencies {
    add(rendererBenchmark.implementationConfigurationName, project(":markdown-renderer"))
    add(rendererBenchmark.implementationConfigurationName, libs.compose.ui)
    add(rendererBenchmark.runtimeOnlyConfigurationName,
        "org.jetbrains.compose.desktop:desktop-jvm-$desktopOs-$desktopArch:${libs.versions.composeMultiplatform.get()}")
}
val rendererFriendArtifacts = configurations[rendererBenchmark.compileClasspathConfigurationName]
    .incoming.artifactView {
        componentFilter { it is ProjectComponentIdentifier && it.projectPath == ":markdown-renderer" }
    }.files
tasks.named<KotlinCompile>(rendererBenchmark.getCompileTaskName("kotlin")) {
    dependsOn(rendererFriendArtifacts)
    compilerOptions.freeCompilerArgs.addAll(rendererFriendArtifacts.elements.map { artifacts ->
        artifacts.map { "-Xfriend-paths=${it.asFile.absolutePath}" }
    })
}

tasks.register<JavaExec>("inlineFlowLayoutBenchmark") {
    group = "benchmark"
    description = "Measures long paragraph layout with text and mixed inline placeholders. Optional argument: maximum character count."
    classpath = rendererBenchmark.runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.InlineFlowLayoutBenchmarkKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    jvmArgs("-Xms256m", "-Xmx512m")
    systemProperty("markdown.benchmark.revision", providers.gradleProperty("benchmarkRevision").getOrElse("working-tree"))
    mustRunAfter("performanceGate", "fullParsingBenchmark", "failedInlineScalingBenchmark",
        "parserCallSampleTimeBenchmark", "growingBlockStreamingBenchmark", "incrementalScalingBenchmark")
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
val performanceGateGeneratedClasses = layout.buildDirectory.dir("benchmarks/main/classes")
val performanceGateGeneratedResources = layout.buildDirectory.dir("benchmarks/main/resources")

val runPerformanceGateBenchmarks = tasks.register<JavaExec>("runPerformanceGateBenchmarks") {
    group = "verification"
    description = "Runs forked parser benchmarks with allocation and peak-heap profilers."
    dependsOn("mainBenchmarkCompile", tasks.named("classes"))
    // Compose the fork classpath from the normal runtime plus generated JMH output. Relying on the
    // kotlinx-benchmark fat JAR is unstable across clean CI builds: it can omit either the original
    // benchmark classes or project dependencies such as MarkdownParser.
    classpath = sourceSets["main"].runtimeClasspath +
        files(performanceGateGeneratedClasses, performanceGateGeneratedResources)
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
    description = "Checks iteration-mean latency percentiles, derived throughput, allocation and observed peak heap."
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

tasks.register<JavaExec>("incrementalScalingBenchmark") {
    group = "benchmark"
    description = "Measures complete append/replace latency and allocation at 100/1000/10000 paragraphs."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.IncrementalScalingBenchmarkKt")
}

tasks.register<JavaExec>("fullParsingBenchmark") {
    group = "benchmark"
    description = "Runs forked full-parse benchmarks for long code, definitions and materialized inline ASTs."
    dependsOn("mainBenchmarkCompile", tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath +
        files(performanceGateGeneratedClasses, performanceGateGeneratedResources)
    mainClass.set("org.openjdk.jmh.Main")
    val results = layout.buildDirectory.file("reports/full-parsing/results.json")
    args("com.hrm.markdown.benchmark.FullParseMicrobenchmark.*", "-prof", "gc",
        "-rf", "json", "-rff", results.get().asFile.absolutePath)
    doFirst { results.get().asFile.parentFile.mkdirs() }
}

tasks.register<JavaExec>("failedInlineScalingBenchmark") {
    group = "benchmark"
    description = "Measures fully materialized failed inline scans at increasing input sizes in forked JMH."
    dependsOn("mainBenchmarkCompile", tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath +
        files(performanceGateGeneratedClasses, performanceGateGeneratedResources)
    mainClass.set("org.openjdk.jmh.Main")
    val results = layout.buildDirectory.file("reports/failed-inline-scaling/results.json")
    args("com.hrm.markdown.benchmark.FailedInlineScalingBenchmark.*", "-prof", "gc",
        "-rf", "json", "-rff", results.get().asFile.absolutePath)
    providers.gradleProperty("failedInlineChars").orNull?.let { args("-p", "chars=$it") }
    providers.gradleProperty("failedInlineShapes").orNull?.let { args("-p", "shape=$it") }
    doFirst { results.get().asFile.parentFile.mkdirs() }
}

tasks.register<JavaExec>("growingBlockStreamingBenchmark") {
    group = "benchmark"
    description = "Measures growing single-block streams, separating buffered input, commits, consumption and finalization."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.hrm.markdown.benchmark.GrowingBlockStreamingBenchmarkKt")
    jvmArgs("-Xms256m", "-Xmx512m")
}

tasks.register<JavaExec>("parserCallSampleTimeBenchmark") {
    group = "benchmark"
    description = "Samples individual fixed-size parser-call latency, with fixture work outside the sampled interval."
    dependsOn("mainBenchmarkCompile", tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath +
        files(performanceGateGeneratedClasses, performanceGateGeneratedResources)
    mainClass.set("org.openjdk.jmh.Main")
    val results = layout.buildDirectory.file("reports/parser-call-sample-time/results.json")
    // Invocation fixtures restore size/AST state; their allocations cannot be attributed to parserCall.
    // Keep this a latency-only suite: the historical allocation gate remains separate and unchanged.
    args("com.hrm.markdown.benchmark.ParserCallSampleTimeBenchmark.*", "-bm", "sample", "-tu", "us",
        "-rf", "json", "-rff", results.get().asFile.absolutePath)
    providers.gradleProperty("sampleTimeParagraphs").orNull?.let { args("-p", "paragraphs=$it") }
    providers.gradleProperty("sampleTimeOperations").orNull?.let { args("-p", "operation=$it") }
    providers.gradleProperty("sampleTimeExistingAst").orNull?.let { args("-p", "existingAst=$it") }
    doFirst { results.get().asFile.parentFile.mkdirs() }
}

// Gradle may schedule independent JavaExec tasks concurrently. Keep measurements on one
// machine sequential, without making an individual suite depend on running the others.
tasks.named("fullParsingBenchmark") { mustRunAfter("performanceGate") }
tasks.named("failedInlineScalingBenchmark") { mustRunAfter("performanceGate", "fullParsingBenchmark") }
tasks.named("parserCallSampleTimeBenchmark") {
    mustRunAfter("performanceGate", "fullParsingBenchmark", "failedInlineScalingBenchmark")
}
tasks.named("growingBlockStreamingBenchmark") {
    mustRunAfter("performanceGate", "fullParsingBenchmark", "failedInlineScalingBenchmark", "parserCallSampleTimeBenchmark")
}
