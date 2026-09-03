// Feature: dynamic-ui-experience, Task 19.1
// Macrobenchmark module — device-only performance & power measurement for the
// DUX-6 performance budgets:
//   * DUX-6.1  cold-start first-meaningful-frame < 1000 ms (StartupBenchmark)
//   * DUX-2.2  Dashboard scroll: 60 fps / native refresh, < 1% dropped frames
//              (ScrollBenchmark, FrameTimingMetric)
//   * DUX-6.6  motion battery overhead <= 1%/hour, motion-on vs motion-off
//              (PowerBenchmark, PowerMetric — supported hardware only)
//
// This is a `com.android.test` module (Macrobenchmark requirement): it hosts an
// APK of instrumentation tests that drive the SEPARATE app under test
// (`targetProjectPath = ":app"`). Macrobenchmarks CANNOT run on the JVM — they
// require a physical device or emulator. The verification goal for task 19.1 is
// that this module and its benchmark sources CONFIGURE and COMPILE.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dopashift.macrobenchmark"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        // The benchmark build type must match the app's `benchmark` build type
        // (declared in app/build.gradle.kts). matchingFallbacks lets it fall
        // back to `release` if the target has no exact match.
        create("benchmark") {
            // Benchmarks are only signed/debuggable enough to install; the app
            // side is profileable + non-debuggable for representative numbers.
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    // The module builds only the `benchmark` variant against the app.
    targetProjectPath = ":app"

    // Macrobenchmark self-instruments the target app; SELF_INSTRUMENTING keeps
    // the benchmark process separate from the app process for accurate timing.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.junit.ext)
    implementation(libs.espresso.core)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

// Only the `benchmark` build type is meaningful for a com.android.test module.
androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
