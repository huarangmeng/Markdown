plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.hrm.markdown.renderer"
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
            baseName = "MarkdownRenderer"
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
            api(projects.markdownParser)
            api(projects.markdownRuntime)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            api(libs.latex.renderer)
            implementation(libs.latex.fonts.katex)
            api(libs.bundles.codehigh)
            api(libs.diagram.render)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation("org.jetbrains.compose.ui:ui-test:${libs.versions.composeMultiplatform.get()}")
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        macosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.java)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

// Opt-in benchmark compiled with the JVM test friend path. It never runs as a JUnit test
// and calls the actual render compiler without creating a window or measuring layout.
val renderCompilerBenchmarkCompilation =
    (kotlin.targets.getByName("jvm") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget)
        .compilations.getByName("test")

tasks.register<JavaExec>("renderCompilerScalingBenchmark") {
    group = "benchmark"
    description = "Measures committed parser updates and the real render compiler at increasing stable-prefix sizes."
    dependsOn("jvmTestClasses")
    classpath = files(
        renderCompilerBenchmarkCompilation.output.allOutputs,
        renderCompilerBenchmarkCompilation.runtimeDependencyFiles,
    )
    mainClass.set("com.hrm.markdown.renderer.internal.core.compile.RenderCompilerScalingBenchmarkKt")
    javaLauncher.set(project.extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>().launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    jvmArgs("-Xms256m", "-Xmx512m")
    mustRunAfter(
        ":markdown-benchmark:performanceGate",
        ":markdown-benchmark:fullParsingBenchmark",
        ":markdown-benchmark:incrementalScalingBenchmark",
        ":markdown-benchmark:failedInlineScalingBenchmark",
        ":markdown-benchmark:parserCallSampleTimeBenchmark",
        ":markdown-benchmark:growingBlockStreamingBenchmark",
    )
}

mavenPublishing {
    publishToMavenCentral(true)

    signAllPublications()

    coordinates(
        "io.github.huarangmeng",
        "markdown-renderer",
        rootProject.property("VERSION").toString()
    )

    pom {
        name.set("Kotlin Multiplatform Markdown Renderer")
        description.set(
            """
            Cross-platform Markdown rendering solution with:
            - Full Markdown syntax support
            - Compose Multiplatform UI integration
            - Custom rendering styles
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
