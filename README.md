# DopaShift

Multi-goal life-tracking platform (Android + Web) that helps users reclaim distracted screen time and redirect it toward personal growth.

## Prerequisites

Install the following before running any part of the project:

| Tool | Version | Notes |
|------|---------|-------|
| Docker & Docker Compose | Latest (Compose V2) | Required for infrastructure services |
| JDK | 17 | Backend and Android builds ([Eclipse Temurin](https://adoptium.net/) recommended) |
| Node.js | 20 LTS+ | Web portal |
| npm | 10+ | Comes with Node.js |
| Android Studio | Latest stable | Android app (includes SDK, emulator) |
| Android SDK | compileSdk 34, minSdk 26 | Install via Android Studio SDK Manager |
| Git | Latest | Source control |

## Repository Structure

```
dopa-shift/
├── backend/        Kotlin/Spring Boot API (Clean/Hexagonal Architecture)
├── android/        Kotlin/Jetpack Compose Android app (multi-module)
├── web/            React/TypeScript web portal (Vite)
├── infra/          Infrastructure configs (Grafana, Keycloak, Loki, Nginx, OTel, Prometheus, Tempo)
├── .github/        CI/CD workflows
└── docker-compose.yml
```

## Quick Start (Full Stack)

### 1. Clone the repository

```bash
git clone <repo-url> dopa-shift
cd dopa-shift
```

### 2. Create your environment file

```bash
cp .env.example .env
```

The defaults in `.env.example` are ready for local development — no changes required to get started.

### 3. Start infrastructure services

```bash
docker compose up -d
```

This starts 12 services:

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL 16 | 5432 | Primary datastore |
| Redis 7 | 6379 | Cache and pub/sub |
| Keycloak 24 | 8443 | Auth (OIDC/OAuth2) |
| MinIO | 9000 (API), 9001 (Console) | Object storage (S3-compatible) |
| Nginx | 80 | Load balancer (round-robin to 2 API instances) |
| API Instance 1 | 8080 | Spring Boot backend |
| API Instance 2 | 8081 | Spring Boot backend |
| Prometheus | 9090 | Metrics collection |
| Grafana | 3000 | Dashboards |
| Loki | 3100 | Log aggregation |
| Tempo | 3200 | Distributed tracing |
| OTel Collector | 4317 (gRPC), 4318 (HTTP) | Telemetry pipeline |

Wait for all services to be healthy:

```bash
docker compose ps
```

### 4. Build and run the backend

```bash
cd backend
./gradlew build
./gradlew bootRun
```

The backend runs on port 8080 by default. If running via Docker Compose, the API placeholder containers need to be replaced with the built JAR (see [Running the Backend in Docker](#running-the-backend-in-docker) below).

### 5. Run the web portal

```bash
cd web
npm install
npm run dev
```

The Vite dev server starts at `http://localhost:5173`.

### 6. Build and run the Android app

1. Open the `android/` directory in Android Studio.
2. Let Gradle sync complete.
3. Select a device/emulator (API 26+).
4. Click **Run** (or `./gradlew assembleDebug` from CLI).

---

## Detailed Setup

### Infrastructure

#### Starting services

```bash
docker compose up -d
```

#### Stopping services

```bash
docker compose down
```

#### Resetting all data (volumes)

```bash
docker compose down -v
```

#### Verifying health

```bash
docker compose ps
# All services should show "healthy" status
```

#### Keycloak

- Admin console: `http://localhost:8443`
- Credentials: `admin` / `admin_dev_pass` (from `.env`)
- The `dopashift` realm is auto-imported from `infra/keycloak/dopashift-realm.json` on first start.

#### MinIO

- Console: `http://localhost:9001`
- Credentials: `minioadmin` / `minio_dev_pass`

#### Grafana

- Dashboard: `http://localhost:3000`
- Credentials: `admin` / `grafana_dev_pass`
- Datasources (Prometheus, Loki, Tempo) are auto-provisioned from `infra/grafana/provisioning/`.

#### Prometheus

- UI: `http://localhost:9090`
- Config: `infra/prometheus/prometheus.yml`
- Alert rules: `infra/prometheus/alert-rules.yml`

---

### Backend (Kotlin / Spring Boot)

The backend uses Clean/Hexagonal Architecture with 4 Gradle modules:

```
backend/
├── domain/          Pure Kotlin domain (entities, value objects, ports)
├── application/     Use cases and application services
├── infrastructure/  JPA repos, Redis, S3, Flyway migrations, Keycloak
└── api/             REST controllers, security, Spring Boot entry point
```

**Tech:** Kotlin 1.9.25, Spring Boot 3.3.5, Gradle 8.10.2, JVM 17

#### Build and test

```bash
cd backend
./gradlew build                              # Compile + test
./gradlew test                               # Unit tests only
./gradlew jacocoTestReport                   # Generate coverage report
./gradlew jacocoTestCoverageVerification     # Enforce 80% line coverage
./gradlew bootRun                            # Run locally (uses application.yml defaults)
./gradlew bootJar                            # Build deployable JAR
```

#### Run against local infrastructure

The backend expects these services running (provided by Docker Compose):
- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- Keycloak on `localhost:8443`
- MinIO on `localhost:9000`
- OTel Collector on `localhost:4317`

When running with `bootRun` outside Docker, update `application.yml` or pass environment variables to point at `localhost` instead of Docker service names:

```bash
./gradlew bootRun --args='--spring.datasource.url=jdbc:postgresql://localhost:5432/dopashift --spring.data.redis.host=localhost'
```

#### Running the backend in Docker

To run API instances within Docker Compose, build the JAR and update the `api-1`/`api-2` services in `docker-compose.yml`:

```bash
cd backend
./gradlew bootJar
```

Then update the `api-1` service in `docker-compose.yml`:
```yaml
api-1:
  image: eclipse-temurin:21-jre-alpine
  container_name: dopashift-api-1
  command: ["java", "-jar", "/app/api.jar"]
  volumes:
    - ./backend/api/build/libs/api-0.0.1-SNAPSHOT.jar:/app/api.jar:ro
  # ... keep remaining config
```

#### Security scans

```bash
./gradlew dependencyCheckAnalyze   # OWASP vulnerability scan
```

---

### Web Portal (React / TypeScript)

```
web/src/
├── domain/    Domain layer (@domain alias)
├── data/      Dexie DB, API client (@data alias)
└── ui/        React components, pages, routing (@ui alias)
```

**Tech:** TypeScript 5.6.3, React 18, Vite 6, Dexie 4, i18next, RxJS 7

#### Install and run

```bash
cd web
npm install
npm run dev          # Dev server at http://localhost:5173
```

#### Build for production

```bash
npm run build        # TypeScript check + Vite production build
npm run preview      # Preview the production build locally
```

#### Lint and format

```bash
npm run lint         # ESLint (zero warnings policy)
npm run format       # Prettier formatting
```

#### Tests

```bash
npm run test -- --coverage --run    # Vitest with coverage (single run)
```

---

### Android App (Kotlin / Jetpack Compose)

```
android/
├── app/            Application entry point, Hilt setup, MainActivity
├── domain/         Pure Kotlin domain (entities, use cases, repo interfaces)
├── data/           Room DB, Retrofit, DataStore, repository implementations
├── ui/             Compose screens, components, design system, theming
├── sync/           Offline-first sync engine (change-log queue, conflict resolution)
└── interception/   Screen-time monitoring, overlay service, usage stats
```

**Tech:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, Retrofit, Coroutines/Flow, Gradle 8.10.2, compileSdk 34, minSdk 26

#### Using Android Studio (recommended)

1. Open `android/` as an existing project in Android Studio.
2. Wait for Gradle sync to complete.
3. Ensure you have an emulator with API 26+ or a connected device.
4. Run the `app` module.

#### Using CLI

```bash
cd android
./gradlew build testDebugUnitTest          # Build + unit tests
./gradlew assembleDebug                    # Debug APK
./gradlew assembleRelease                  # Release APK
./gradlew connectedDebugAndroidTest        # Instrumented tests (requires emulator/device)
./gradlew jacocoTestReport                 # Coverage report
```

#### API configuration

The Android app connects to the backend API. Update the base URL in the data module's network configuration to point to your local machine's IP (not `localhost`, which resolves to the emulator itself):

- Emulator: use `10.0.2.2` to reach the host machine
- Physical device: use your machine's LAN IP

---

## Environment Variables

All environment variables are documented in `.env.example`. Key groups:

| Group | Variables | Defaults |
|-------|-----------|----------|
| PostgreSQL | `POSTGRES_*` | `dopashift` / `dopashift_dev_pass` |
| Redis | `REDIS_*` | port 6379, password `redis_dev_pass` |
| Keycloak | `KEYCLOAK_*` | admin/`admin_dev_pass`, realm `dopashift` |
| MinIO | `MINIO_*` | `minioadmin` / `minio_dev_pass` |
| API | `API_PORT_1`, `API_PORT_2` | 8080, 8081 |
| Observability | `OTEL_*`, `PROMETHEUS_*`, `GRAFANA_*`, `LOKI_*` | Standard dev ports |

---

## CI/CD

GitHub Actions workflows in `.github/workflows/`:

- **`ci.yml`** (every commit/PR): Semgrep scan, backend build+test+JaCoCo, OWASP dep check, web lint+typecheck+Vitest+build, Android build+unit tests, API contract tests.
- **`release.yml`** (release builds): Playwright E2E, Android instrumented tests (API 34 emulator), artifact builds (JAR, web dist, APK).

---

## Useful Commands Cheat Sheet

| What | Command | Run from |
|------|---------|----------|
| Start all infra | `docker compose up -d` | repo root |
| Stop all infra | `docker compose down` | repo root |
| Backend build+test | `./gradlew build` | `backend/` |
| Backend run | `./gradlew bootRun` | `backend/` |
| Backend coverage | `./gradlew jacocoTestReport` | `backend/` |
| Web dev server | `npm run dev` | `web/` |
| Web build | `npm run build` | `web/` |
| Web tests | `npm run test -- --coverage --run` | `web/` |
| Android build | `./gradlew build` | `android/` |
| Android unit tests | `./gradlew testDebugUnitTest` | `android/` |
| Android instrumented | `./gradlew connectedDebugAndroidTest` | `android/` |

---

## Troubleshooting

### Docker services failing to start

- Ensure Docker Desktop is running and has enough memory allocated (8 GB+ recommended for all 12 services).
- Check if ports are already in use: `netstat -ano | findstr :5432` (Windows) or `lsof -i :5432` (macOS/Linux).
- View logs: `docker compose logs <service-name>`.

### Keycloak not starting

Keycloak depends on PostgreSQL. Ensure Postgres is healthy first:
```bash
docker compose ps postgres
```

### Backend cannot connect to services

When running outside Docker (via `bootRun`), services are on `localhost`, not their Docker network names. Override connection properties via environment variables or Spring profiles.

### Android emulator cannot reach the backend

Use `10.0.2.2` (emulator's alias for host loopback) instead of `localhost` in API base URL configuration.

### Gradle build issues

- Ensure `JAVA_HOME` points to JDK 17.
- On Windows, use `.\gradlew.bat` instead of `./gradlew`.

### Web portal `npm install` fails

- Ensure Node.js 20+ is installed: `node --version`.
- Delete `node_modules` and `package-lock.json`, then retry: `Remove-Item -Recurse node_modules; npm install`.
