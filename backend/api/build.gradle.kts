plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // API module is the Spring Boot entry point
    // Depends on all other modules
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    // Spring Boot starters needed from infrastructure (transitive)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Rate Limiting (using Redis via Spring Data Redis for distributed rate limiting)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Observability: Prometheus metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Observability: Distributed tracing via Micrometer + OpenTelemetry bridge
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.35.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.35.0")

    // Structured JSON logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    mainClass.set("com.dopashift.api.DopaShiftApplicationKt")
}
