plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Coroutines (pure Kotlin, no Android)
    implementation(libs.coroutines.core)

    // Javax inject for DI annotations (framework-agnostic)
    implementation("javax.inject:javax.inject:1")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
