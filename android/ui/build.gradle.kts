plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dopashift.ui"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Required for Robolectric-based unit tests that drive the
            // RuleAuthoringViewModel (its InstalledAppsProvider queries a
            // Context-backed PackageManager).
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Run unit tests on the JUnit Platform so Kotest (JUnit 5) property tests run;
// the vintage engine keeps the existing JUnit 4 / Robolectric tests running.
tasks.withType<Test> {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// DUX static-analysis governance gate (task 20.1 — DUX-4.1, DUX-4.11, DUX-2.1,
// DUX-7.3).
//
// `verifyDesignTokens` scans the ui module's MAIN Kotlin source for three
// classes of forbidden literals and fails the build (GradleException with a
// file:line list) if any are found in feature code. It is wired into `check`
// so CI enforces it automatically.
//
// Rules:
//   1. Raw hex color literals used as colors — `Color(0x...)` — outside the
//      token-definition files (theme tokens must come from the design system /
//      DopaShiftTheme, per DUX-4.1).
//   2. Raw animation duration literals — `tween(<number>` or
//      `durationMillis = <number>` — outside MotionTokens.kt (motion must
//      reference the four named MotionTokens specs, per DUX-2.1).
//   3. Hardcoded user-facing string literals — `Text("<non-empty>"` — outside
//      the token files and the pre-DUX screens that predate string
//      externalization; user-facing copy must use stringResource (DUX-4.11).
//
// Exclusions (never scanned): the design-system token/definition files, any
// *Tokens*.kt, and test sources. The string rule additionally excludes the
// screens that predate the DUX string-externalization work (task 17), the same
// way predating interception code is excluded — the rule stays capable of
// failing on any NEW violation introduced in DUX feature composables.
// ---------------------------------------------------------------------------
val verifyDesignTokens by tasks.registering {
    group = "verification"
    description = "Fails on raw color/duration literals or hardcoded UI strings in ui composables (DUX-4.1/DUX-2.1/DUX-4.11)."

    // Token/definition files excluded from ALL rules — these legitimately
    // define the raw color/motion primitives the rest of the app references.
    val tokenDefinitionFiles = setOf(
        "MotionTokens.kt",
        "Theme.kt",
        "ThemeState.kt",
        "Color.kt",
        "Shape.kt",
        "Spacing.kt",
        "Accessibility.kt",
        "Type.kt",
        "OverlayBackground.kt",
    )
    // Screens that predate the DUX string-externalization (task 17) and are not
    // part of the DUX feature's externalized surface. Excluded from the string
    // rule only (they still get the color/motion rules). Tightening scope here
    // is preferred over weakening the string pattern, per the task guidance.
    val preDuxStringScreens = setOf(
        "DailyTasksScreen.kt",
        "SettingsScreen.kt",
        "RemindersScreen.kt",
        "OnboardingScreen.kt",
        "GoalsScreen.kt",
        // AnalyticsScreen predates the DUX string-externalization too: its copy
        // is still authored inline (text = "Analytics", "Summary", etc.), so it
        // belongs with the other un-externalized screens rather than being
        // half-fixed. Excluded from the string rule only.
        "AnalyticsScreen.kt",
    )

    val sourceRoots = listOf(
        layout.projectDirectory.dir("src/main/java"),
        layout.projectDirectory.dir("src/main/kotlin"),
    )
    inputs.files(sourceRoots.map { it.asFileTree.matching { include("**/*.kt") } })

    doLast {
        // Rule 1: raw hex color literal used as a Color — Color(0x...)
        val rawColorRegex = Regex("""Color\s*\(\s*0x""")
        // Rule 2: raw animation durations — tween(<number> OR durationMillis = <number>
        val rawTweenRegex = Regex("""tween\s*\(\s*\d""")
        val rawDurationRegex = Regex("""durationMillis\s*=\s*\d""")
        // Rule 3: hardcoded user-facing string as Text's first arg — Text("<non-empty>"
        val hardcodedTextRegex = Regex("""\bText\s*\(\s*"[^"]""")

        data class Violation(val rule: String, val file: File, val line: Int, val text: String)
        val violations = mutableListOf<Violation>()

        sourceRoots.forEach { root ->
            val dir = root.asFile
            if (!dir.exists()) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val name = file.name
                    val isTokenFile = name in tokenDefinitionFiles || name.contains("Tokens")
                    file.readLines().forEachIndexed { idx, raw ->
                        val lineNo = idx + 1
                        val line = raw
                        // Skip comment-only lines to avoid flagging documentation.
                        val trimmed = line.trimStart()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

                        if (!isTokenFile) {
                            if (rawColorRegex.containsMatchIn(line)) {
                                violations += Violation("Raw color literal (use theme tokens — DUX-4.1)", file, lineNo, line.trim())
                            }
                            if (rawTweenRegex.containsMatchIn(line) || rawDurationRegex.containsMatchIn(line)) {
                                violations += Violation("Raw animation duration (use MotionTokens — DUX-2.1)", file, lineNo, line.trim())
                            }
                            if (name !in preDuxStringScreens && hardcodedTextRegex.containsMatchIn(line)) {
                                violations += Violation("Hardcoded UI string (use stringResource — DUX-4.11)", file, lineNo, line.trim())
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            val report = violations.joinToString("\n") { v ->
                "  [${v.rule}]\n    ${v.file.absolutePath}:${v.line}\n      ${v.text}"
            }
            throw GradleException(
                "verifyDesignTokens found ${violations.size} DUX governance violation(s):\n$report\n" +
                    "Fix: use DopaShiftTheme/theme tokens for colors, MotionTokens for durations, " +
                    "and stringResource(...) for user-facing text.",
            )
        }
        logger.lifecycle("verifyDesignTokens: no DUX token/string/motion violations found.")
    }
}

tasks.named("check") {
    dependsOn(verifyDesignTokens)
}

dependencies {
    implementation(project(":domain"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.windowsizeclass)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.text.google.fonts)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    // WindowManager (foldable / hinge features via WindowInfoTracker)
    implementation(libs.window)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Kotest property-based testing (runs on the JUnit 5 platform).
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertions.core)
    // Vintage engine so existing JUnit 4 / Robolectric tests keep running on
    // the JUnit Platform alongside the Kotest (JUnit 5) property tests.
    testRuntimeOnly(libs.junit.vintage.engine)
    // Robolectric supplies an Android Context on the JVM so property tests can
    // drive the real RuleAuthoringViewModel (which constructs an
    // InstalledAppsProvider backed by a PackageManager).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit.ext)
    // Compose UI testing on the JVM via Robolectric (createComposeRule works
    // under RobolectricTestRunner). ui-test-manifest supplies the empty
    // activity the test host launches into.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
