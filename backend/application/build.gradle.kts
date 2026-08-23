plugins {
    kotlin("jvm")
}

dependencies {
    // Application layer depends on Domain
    implementation(project(":domain"))

    // Coroutines for suspend functions
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Jackson for JSON payload parsing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
}
