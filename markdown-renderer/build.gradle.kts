plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.hrm.markdown.renderer"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
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

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.bundles.latex)

            // 图片加载：Coil3 + Ktor3 网络引擎
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
        iosMain.dependencies {
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
            - Multi-platform support (Android/iOS/JVM/JS/WasmJS)
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
                id.set("huaranmeng")
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
