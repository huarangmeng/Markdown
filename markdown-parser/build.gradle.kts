import java.time.Duration

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.hrm.markdown.parser"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("consumer-rules.pro"))
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "MarkdownParser"
            isStatic = true
        }
    }

    macosArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val parserJvmTest = tasks.named<Test>("jvmTest") {
    exclude("**/ParserStressProcessTest.class")
}

tasks.register<Test>("stressTest") {
    group = "verification"
    description = "Checks extreme parser inputs in time- and memory-limited child JVMs."
    val ordinaryTest = parserJvmTest.get()
    testClassesDirs = ordinaryTest.testClassesDirs
    classpath = ordinaryTest.classpath
    javaLauncher.set(ordinaryTest.javaLauncher)
    include("**/ParserStressProcessTest.class")
    useJUnit()
    maxHeapSize = "128m"
    maxParallelForks = 1
    timeout.set(Duration.ofMinutes(8))
    outputs.upToDateWhen { false }
    outputs.cacheIf("Resource budgets need fresh child JVMs") { false }
    val stressReports = layout.buildDirectory.dir("reports/parser-stress")
    outputs.dir(stressReports)
    systemProperty("markdown.stress.reportDir", stressReports.get().asFile.absolutePath)
    doFirst {
        val testTask = this as Test
        testTask.systemProperty("markdown.stress.classpath", testTask.classpath.asPath)
    }
}

tasks.named("check") { dependsOn("stressTest") }

mavenPublishing {
    publishToMavenCentral(true)

    signAllPublications()

    coordinates(
        "io.github.huarangmeng",
        "markdown-parser",
        rootProject.property("VERSION").toString()
    )

    pom {
        name.set("Kotlin Multiplatform Markdown Parser")
        description.set(
            """
            Cross-platform Markdown parsing solution with:
            - Full Markdown syntax support
            - AST (Abstract Syntax Tree) generation
            - Incremental parsing support
            - Multi-platform support (Android/iOS/macOS Native/JVM/JS/WasmJS)
        """.trimIndent()
        )
        inceptionYear.set("2026")
        url.set("https://github.com/huarangmeng/Markdown")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("huarangmeng")
                name.set("Kotlin Multiplatform Specialist")
                url.set("https://github.com/huarangmeng/")
            }
        }
        scm {
            url.set("https://github.com/huarangmeng/Markdown")
            connection.set("scm:git:git://github.com/huarangmeng/Markdown.git")
            developerConnection.set("scm:git:ssh://git@github.com/huarangmeng/Markdown.git")
        }
    }
}
