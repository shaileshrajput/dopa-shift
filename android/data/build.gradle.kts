plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.dopashift.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    // Expose the exported Room schema JSONs to instrumented tests (used by the migration test).
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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

    testOptions {
        unitTests {
            // Required for Robolectric-based unit tests (e.g. Context-backed DataStore).
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

ksp {
    // Export Room schemas (matches @Database exportSchema = true) so migrations are testable.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Moshi
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Security (encrypted storage for LLM API keys)
    implementation(libs.security.crypto)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Kotest property-based testing (runs on the JUnit 5 platform).
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertions.core)
    // Vintage engine so the JUnit 4 / Robolectric property tests keep running on
    // the JUnit Platform alongside the Kotest (JUnit 5) property tests.
    testRuntimeOnly(libs.junit.vintage.engine)
    // Robolectric enables JVM unit tests that need an Android Context
    // (e.g. the Context-backed DataStore used by AggregatedCounterSync and
    // QuickCreatePreferencesStore).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit.ext)

    // Instrumented testing (Room migration tests)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.testing)
}
