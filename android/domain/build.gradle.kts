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

    // Kotest property-based testing (runs on the JUnit 5 platform)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertions.core)

    // Vintage engine so existing JUnit 4 tests keep running on the JUnit Platform
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
