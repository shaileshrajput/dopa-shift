---
inclusion: always
---

# DopaShift — Tech Stack & Build

## Backend (Kotlin / Spring Boot)

| Concern | Technology |
|---------|-----------|
| Language | Kotlin 1.9.25, JVM 17 |
| Framework | Spring Boot 3.3.5 (Web, WebFlux, Security, Data JPA, Data Redis, Actuator, Validation, OAuth2 Resource Server) |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache / Pub-Sub | Redis 7 |
| Auth | Keycloak 24 (OIDC/OAuth2) |
| Object Storage | MinIO (AWS SDK v2) |
| Observability | Micrometer + Prometheus, OpenTelemetry tracing, Loki logs (Logstash Logback encoder), Grafana, Tempo |
| Testing | JUnit 5, Kotest (property-based), Spring Boot Test, JaCoCo (80% coverage gate) |
| Security Scanning | Semgrep (SAST + secrets), OWASP Dependency-Check |
| Architecture | Clean/Hexagonal — 4 modules: `domain`, `application`, `infrastructure`, `api` |

## Android (Kotlin / Jetpack Compose)

| Concern | Technology |
|---------|-----------|
| Language | Kotlin, JVM 17, compileSdk/targetSdk 34, minSdk 26 |
| UI | Jetpack Compose (Material 3, Compose BOM) |
| DI | Hilt (KSP) |
| Local DB | Room (KSP) |
| Networking | Retrofit + OkHttp + Moshi (KSP codegen) |
| Async | Kotlin Coroutines + Flow |
| Preferences | DataStore |
| Logging | Timber |
| Navigation | Navigation Compose |
| Architecture | Multi-module — 6 modules: `app`, `domain`, `data`, `ui`, `sync`, `interception` |

## Web Portal (React / TypeScript)

| Concern | Technology |
|---------|-----------|
| Language | TypeScript 5.6 |
| Framework | React 18, React Router DOM 6 |
| Build | Vite 6 (code-splitting: vendor, dexie, i18n chunks) |
| Local DB | Dexie 4 (IndexedDB) |
| i18n | i18next + react-i18next |
| Reactive | RxJS 7 |
| Linting | ESLint 8 + Prettier 3 |
| Testing | Vitest (unit, 70% coverage gate), Playwright (E2E) |
| Architecture | Layer separation via path aliases: `@domain`, `@data`, `@ui` |

## Infrastructure (Docker Compose)

PostgreSQL 16, Redis 7, Keycloak 24, MinIO, Nginx (load balancer, 2 API instances), OpenTelemetry Collector, Prometheus, Grafana, Loki, Tempo.

## CI/CD (GitHub Actions)

- **CI (`ci.yml`):** Semgrep secret scan → backend build+test+JaCoCo → OWASP dep scan → web lint+typecheck+Vitest+build → Android build+unit tests → API contract tests.
- **Release (`release.yml`):** Playwright E2E → Android instrumented tests (emulator API 34) → build all artifacts (JAR, web dist, APK).

## Common Commands

### Backend (run from `backend/`)

```bash
./gradlew build test jacocoTestReport       # Build + test + coverage
./gradlew jacocoTestCoverageVerification    # Enforce 80% line coverage
./gradlew bootJar                           # Build deployable JAR
./gradlew dependencyCheckAnalyze            # OWASP vulnerability scan
./gradlew contractTest                      # API contract tests
```

### Android (run from `android/`)

```bash
./gradlew build testDebugUnitTest           # Build + unit tests
./gradlew connectedDebugAndroidTest         # Instrumented tests (emulator)
./gradlew assembleRelease                   # Release APK
./gradlew jacocoTestReport                  # Coverage report
```

### Web Portal (run from `web/`)

```bash
npm run dev                                 # Vite dev server
npm run build                               # Production build (TS + Vite)
npm run lint                                # ESLint (zero warnings)
npm run format                              # Prettier
npm run test -- --coverage --run            # Vitest with coverage
```

### Infrastructure

```bash
docker compose up -d                        # Start all services
docker compose down                         # Stop all services
```
