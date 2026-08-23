---
inclusion: always
---

# DopaShift — Project Structure

```
dopa-shift/
├── backend/                      # Spring Boot API (Kotlin, Clean Architecture)
│   ├── domain/                   # Pure Kotlin domain entities, value objects, ports (zero framework deps)
│   ├── application/              # Use cases, application services, DTOs
│   ├── infrastructure/           # JPA repositories, Redis, S3/MinIO, Flyway, Keycloak integration
│   └── api/                      # REST controllers, security config, Spring Boot entry point
│
├── android/                      # Android app (Kotlin, Jetpack Compose)
│   ├── app/                      # Application entry point, Hilt setup, MainActivity
│   ├── domain/                   # Pure Kotlin domain (entities, use cases, repository interfaces)
│   ├── data/                     # Room DB, Retrofit API, repository implementations, DataStore
│   ├── ui/                       # Compose screens, components, design system, theming
│   ├── sync/                     # Offline-first sync engine (change-log queue, conflict resolution)
│   └── interception/             # Screen-time monitoring, overlay service, usage stats
│
├── web/                          # React/TypeScript web portal
│   └── src/
│       ├── domain/               # Domain layer (@domain alias)
│       ├── data/                 # Dexie DB, API client (@data alias)
│       └── ui/                   # React components, pages, routing (@ui alias)
│
├── infra/                        # Infrastructure configuration
│   ├── grafana/                  # Dashboard provisioning
│   ├── keycloak/                 # Realm JSON (dopashift-realm.json)
│   ├── loki/                     # Log aggregation config
│   ├── nginx/                    # Load balancer config (2 API instances)
│   ├── otel/                     # OpenTelemetry Collector config
│   ├── prometheus/               # Metrics + alert rules
│   └── tempo/                    # Distributed tracing config
│
├── .github/workflows/            # CI (ci.yml) and release (release.yml) pipelines
├── .kiro/                        # Kiro specs, steering, and agent config
├── docker-compose.yml            # Full local dev environment (12 services)
├── dopa-shift-requirements.md    # Comprehensive product requirements
├── .env.example                  # Environment variables template
└── .semgrep.yml                  # SAST and secret detection rules
```

## Architecture Patterns

### Backend — Clean/Hexagonal Architecture

Dependency direction: `api → application → domain ← infrastructure`

- **domain:** No Spring/framework imports. Contains entities, value objects, enums, repository ports (interfaces), and domain services.
- **application:** Orchestrates use cases. Depends only on domain ports. Contains application services and command/query DTOs.
- **infrastructure:** Implements domain ports. Contains JPA entities/repos, Redis client, S3 client, Flyway migrations, external integrations.
- **api:** Spring Boot entry point. REST controllers, security filters, rate limiting, request/response mapping.

### Android — Multi-Module with Clean Separation

Dependency direction: `app → [domain, data, ui, sync, interception]`, all feature modules depend on `domain`

- **domain:** Pure Kotlin. Shared across all modules. No Android framework deps.
- **data:** Implements domain repository interfaces using Room, Retrofit, DataStore.
- **ui:** Compose-only module. Screens, navigation graphs, design tokens.
- **sync:** Handles offline queue, change-log events, conflict resolution, background sync workers.
- **interception:** UsageStatsManager polling, overlay service, foreground service lifecycle.

### Web — Layer Separation via Path Aliases

- `@domain` → `src/domain/` (models, interfaces)
- `@data` → `src/data/` (Dexie schemas, API client, sync logic)
- `@ui` → `src/ui/` (React components, pages, hooks)

## Key Conventions

- All three platforms share the same layered architecture pattern (domain → data/infra → presentation/api).
- The `domain` layer is always pure language code with no framework dependencies.
- Repository interfaces are defined in `domain`; implementations live in `data`/`infrastructure`.
- Offline-first: writes always go to local storage first; sync is eventual.
- Localization: UI strings in `en`, `hi`, `mr` (English, Hindi, Marathi).
