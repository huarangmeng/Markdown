plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":markdown-renderer"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.hrm.markdown.benchmark.StreamingRenderBenchmarkKt")
}
